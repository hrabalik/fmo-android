# PA
![Java CI with Gradle](https://github.com/sverbach/fmo-android/workflows/Java%20CI%20with%20Gradle/badge.svg?branch=master)

Projektarbeit 2020 Mobile Table Tennis Tracking System (M3TS)

## Ziel
Ziel der Arbeit ist es, ein mobiles App zu entwickeln, welches den Punktestand eines Tischtennis-Spiels tracked und anzeigt / ausgibt. Das Tracking und Anzeigen des Matches soll in Echtzeit durchgeführt werden.
Es soll hierbei kein neuartiger Object-Tracking Algorithmus entwickelt werden, dieser Aspekt wird von bestehenden Open Source Projekte / Libraries übernommen.

### Aufnahme des Tisches
Der Tischtennistisch soll seitlich mit einem Smartphone-Rückkamera gefilmt werden, dabei kann ein Stativ o.Ä. zur Unterstützung genutzt werden. 

Folgendes Bild verdeutlicht den Aufbau der Aufnahme:
![Seitliche Aufnahme eines Tischtennistisches](https://i.ibb.co/kyFCQfZ/M3TS.jpg)

## Technologie
Für das Tracking des Balles soll eine Library verwendet werden.
Mögliche Tracking Libraries 
- [fmo-android](https://github.com/hrabalik/fmo-android "fast moving objects algorithm")
- **TODO: finde mehr**

Die Lösung soll unter dem **Android** Betriebssystem lauffähig sein.

## Testkonzept
Das Testkonzept setzt sich zusammen aus CI und Code Test Coverage Monitoring in SonarQube.
Es werden Unit Tests (JUnit 4, Mockito, PowerMock) sowie Instrumentation Tests (JUnit 4, Mockito, Android Runner) erstellt um den Quellcode zu testen.

### CI
Bei jedem Push / PR in den master werden 2 Github Workflows getriggert:

- **Build** - Buildet das Projekt
- **Unit and Instrumentation Tests** - Lässt Unit und Instrumentation Tests laufen (benötigt Android Emulator) und erstellt mit JaCoCo einen Coverage Report, welcher anschliessend auf SonarQube geladen wird.

Link zur Übersicht der Workflows: https://github.com/sverbach/m3ts/actions
### SonarQube
Link zum Dashboard: https://sonarcloud.io/dashboard?id=sverbach_fmo-android

## Abgrenzungen
Für das Tracken des Spiels und die Berechnung der Ballposition darf lediglich ein Smartphone verwendet werden.
Die Anzeige / Ausgabe des aktuellen Punktestand soll bzw. darf auf einem zweiten Smartphone geschehen.
