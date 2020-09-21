#include "desktop-opencv.hpp"
#include "loop.hpp"
#include "recorder.hpp"
#include <algorithm>
#include <fmo/processing.hpp>
#include <fmo/region.hpp>
#include <iostream>

DebugVisualizer::DebugVisualizer(Status& s) {
    s.window.setBottomLine("[esc] quit | [space] pause | [enter] step | [,][.] jump 10 frames");
}

void DebugVisualizer::visualize(Status& s, const fmo::Region&, const Evaluator* evaluator,
                                const EvalResult& evalResult, fmo::Algorithm& algorithm) {
    // draw the debug image provided by the algorithm
    fmo::copy(algorithm.getDebugImage(), mVis);
    s.window.print(s.inputName);
    s.window.print("frame: " + std::to_string(s.inFrameNum));

    // get pixel coordinates of detected objects
    algorithm.getOutput(mOutputCache);
    mObjectPoints.clear();
    for (auto& detection : mOutputCache.detections) {
        mObjectPoints.emplace_back();
        detection->getPoints(mObjectPoints.back());
    }

    // draw detected points vs. ground truth
    if (evaluator != nullptr) {
        s.window.print(evalResult.str());
        auto& gt = evaluator->gt().get(s.outFrameNum);
        fmo::pointSetMerge(begin(mObjectPoints), end(mObjectPoints), mPointsCache);
        fmo::pointSetMerge(begin(gt), end(gt), mGtPointsCache);
        drawPointsGt(mPointsCache, mGtPointsCache, mVis);
        s.window.setTextColor(good(evalResult.eval) ? Colour::green() : Colour::red());
    } else {
        drawPoints(mPointsCache, mVis, Colour::lightMagenta());
    }

    // display
    s.window.display(mVis);

    // process keyboard input
    bool step = false;
    do {
        auto command = s.window.getCommand(s.paused);
        if (command == Command::PAUSE) s.paused = !s.paused;
        if (command == Command::STEP) step = true;
        if (command == Command::QUIT) s.quit = true;
        if (command == Command::SCREENSHOT) fmo::save(mVis, "screenshot.png");

        if (!s.haveCamera()) {
            if (command == Command::JUMP_BACKWARD) {
                s.paused = false;
                s.args.frame = std::max(1, s.inFrameNum - 10);
                s.reload = true;
            }
            if (command == Command::JUMP_FORWARD) {
                s.paused = false;
                s.args.frame = s.inFrameNum + 10;
            }
        }
    } while (s.paused && !step && !s.quit);
}

DemoVisualizer::DemoVisualizer(Status& s) : mStats(60) {
    mStats.reset(15.f);
    updateHelp(s);
}

void DemoVisualizer::updateHelp(Status& s) {
    if (!mShowHelp) {
        s.window.setBottomLine("");
    } else {
        std::ostringstream oss;
        oss << "[esc] quit";

        if (mAutomatic) {
            oss << " | [m] manual mode | [e] forced event";
        } else {
            oss << " | [a] automatic mode | [r] start/stop recording";
        }

        if (s.sound) {
            oss << " | [s] disable sound";
        } else {
            oss << " | [s] enable sound";
        }

        s.window.setBottomLine(oss.str());
    }
}

void DemoVisualizer::printStatus(Status& s) const {
    bool recording;

    if (mAutomatic) {
        s.window.print("automatic mode");
        recording = mAutomatic->isRecording();
    } else {
        s.window.print("manual mode");
        recording = bool(mManual);
    }

    s.window.print(recording ? "recording" : "not recording");
    s.window.print("events: " + std::to_string(mEventsDetected));
    s.window.print("[?] for help");

    s.window.setTextColor(recording ? Colour::lightRed() : Colour::lightGray());
}

void DemoVisualizer::onDetection(const Status& s, const fmo::Algorithm::Detection& detection) {
    // register a new event after a time without detections
    if (s.outFrameNum - mLastDetectFrame > EVENT_GAP_FRAMES) {
        if (s.sound) {
            // make some noise
            std::cout << char(7);
        }
        mEventsDetected++;
        mSegments.clear();
    }
    mLastDetectFrame = s.outFrameNum;

    // don't add a segment if there is no previous center
    if (!detection.predecessor.haveCenter()) { return; }

    // add a segment
    fmo::Bounds segment{detection.predecessor.center, detection.object.center};
    mSegments.push_back(segment);

    // make sure to keep the number of segments bounded in case there's a long event
    if (mSegments.size() > MAX_SEGMENTS) {
        mSegments.erase(begin(mSegments), begin(mSegments) + (mSegments.size() / 2));
    }
}

void DemoVisualizer::drawSegments(fmo::Image& im) {
    cv::Mat mat = im.wrap();
    auto color = Colour::magenta();

    for (auto& segment : mSegments) {
        color.b = std::max(color.b, uint8_t(color.b + 2));
        color.g = std::max(color.g, uint8_t(color.g + 1));
        color.r = std::max(color.r, uint8_t(color.r + 4));
        cv::Scalar cvColor(color.b, color.g, color.r);
        cv::Point pt1{segment.min.x, segment.min.y};
        cv::Point pt2{segment.max.x, segment.max.y};
        cv::line(mat, pt1, pt2, cvColor, 8);
    }
}

void DemoVisualizer::visualize(Status& s, const fmo::Region& frame, const Evaluator*,
                               const EvalResult&, fmo::Algorithm& algorithm) {
    // estimate FPS
    mStats.tick();
    auto fpsEstimate = [this]() { return std::round(mStats.quantilesHz().q50); };

    // record frames
    algorithm.getOutput(mOutput);
    if (mAutomatic) {
        bool event = mForcedEvent || !mOutput.detections.empty();
        mAutomatic->frame(frame, event);
    } else if (mManual) {
        mManual->frame(frame);
    }
    mForcedEvent = false;

    // draw input image as background
    fmo::copy(frame, mVis);

    // iterate over detected fast-moving objects
    for (auto& detection : mOutput.detections) { onDetection(s, *detection); }

    // display
    drawSegments(mVis);
    printStatus(s);
    s.window.display(mVis);

    // process keyboard input
    auto command = s.window.getCommand(false);
    if (command == Command::QUIT) { s.quit = true; }
    if (command == Command::SHOW_HELP) {
        mShowHelp = !mShowHelp;
        updateHelp(s);
    }
    if (command == Command::AUTOMATIC_MODE) {
        if (mManual) { mManual.reset(nullptr); }
        if (!mAutomatic) {
            mAutomatic = std::make_unique<AutomaticRecorder>(s.args.recordDir, frame.format(),
                                                             frame.dims(), fpsEstimate());
            updateHelp(s);
        }
    }
    if (command == Command::FORCED_EVENT) { mForcedEvent = true; }
    if (command == Command::MANUAL_MODE) {
        if (mAutomatic) {
            mAutomatic.reset(nullptr);
            updateHelp(s);
        }
    }
    if (command == Command::RECORD) {
        if (mManual) {
            mManual.reset(nullptr);
        } else if (!mAutomatic) {
            mManual = std::make_unique<ManualRecorder>(s.args.recordDir, frame.format(),
                                                       frame.dims(), fpsEstimate());
        }
    }
    if (command == Command::PLAY_SOUNDS) { s.sound = !s.sound; }
}
