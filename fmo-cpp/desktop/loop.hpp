#ifndef FMO_DESKTOP_LOOP_HPP
#define FMO_DESKTOP_LOOP_HPP

#include "args.hpp"
#include "calendar.hpp"
#include "evaluator.hpp"
#include "report.hpp"
#include "window.hpp"
#include <fmo/algorithm.hpp>
#include <fmo/retainer.hpp>
#include <fmo/stats.hpp>

struct Visualizer;
struct AutomaticRecorder;
struct ManualRecorder;

struct Status {
    Args args;                              ///< user settings
    Window window;                          ///< GUI handle
    Results results;                        ///< evaluation results
    Results baseline;                       ///< previous evaluation results
    Date date;                              ///< date and time at the start of the evaluation
    fmo::Timer timer;                       ///< timer for the whole run
    std::string inputName;                  ///< name of the currently played back input
    std::unique_ptr<Visualizer> visualizer; ///< visualization method
    std::unique_ptr<DetectionReport> rpt;   ///< detection report file writer
    int inFrameNum;                         ///< frame number of input video (first frame = frame 1)
    int outFrameNum;                        ///< frame number of detected objects and GT
    bool paused = false;                    ///< playback paused
    bool quit = false;                      ///< exit application now
    bool reload = false;                    ///< load the same video again
    bool sound = false;                     ///< play sounds

    Status(int argc, char** argv) : args(argc, argv) {}
    bool haveCamera() const { return args.camera != -1; }
    bool haveWait() const { return args.wait != -1; }
    bool haveFrame() const { return args.frame != -1; }
    void unsetFrame() { args.frame = -1; }
};

void processVideo(Status& s, size_t inputNum);

struct Visualizer {
    virtual void visualize(Status& s, const fmo::Region& frame, const Evaluator* evaluator,
                           const EvalResult& evalResult, fmo::Algorithm& algorithm) = 0;
};

struct DebugVisualizer : public Visualizer {
    DebugVisualizer(Status& s);

    virtual void visualize(Status& s, const fmo::Region& frame, const Evaluator* evaluator,
                           const EvalResult& evalResult, fmo::Algorithm& algorithm) override;

private:
    fmo::Image mVis;
    fmo::Algorithm::Output mOutputCache;
    fmo::Retainer<fmo::PointSet, 6> mObjectPoints;
    fmo::PointSet mPointsCache;
    fmo::PointSet mGtPointsCache;
};

struct DemoVisualizer : public Visualizer {
    DemoVisualizer(Status& s);

    virtual void visualize(Status& s, const fmo::Region& frame, const Evaluator* evaluator,
                           const EvalResult& evalResult, fmo::Algorithm& algorithm) override;

private:
    static constexpr int EVENT_GAP_FRAMES = 12;
    static constexpr size_t MAX_SEGMENTS = 200;

    void updateHelp(Status& s);
    void printStatus(Status& s) const;
    void onDetection(const Status& s, const fmo::Algorithm::Detection& detection);
    void drawSegments(fmo::Image& im);

    fmo::FrameStats mStats;                        ///< frame rate estimator
    bool mShowHelp = false;                        ///< show help on the GUI?
    bool mForcedEvent = false;                     ///< force an event in the next frame?
    std::unique_ptr<AutomaticRecorder> mAutomatic; ///< for automatic-mode recording
    std::unique_ptr<ManualRecorder> mManual;       ///< for manual-mode recording
    fmo::Image mVis;                               ///< cached image buffer
    fmo::Algorithm::Output mOutput;                ///< cached output object
    std::vector<fmo::Bounds> mSegments;            ///< object path being visualized
    int mEventsDetected = 0;                       ///< event counter
    int mLastDetectFrame = -EVENT_GAP_FRAMES;      ///< the frame when the last detection happened
};

#endif // FMO_DESKTOP_LOOP_HPP
