# Melodigram

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

An interactive **MIDI file player and visualizer**, built with Java.  
Watch musical pieces come to life with an animated piano display — or connect your own MIDI keyboard to learn new pieces.  

---

## Demo

![demogif2](https://github.com/user-attachments/assets/78767fd0-896c-4d78-8ce3-84d4993e78b8)

---


## About The Project

This is a purely experimental hobby project that aims to provide a tool for people with no musical background who want to start learning piano.

Unlike most Synthesia-like applications, which are typically built with game engines or multimedia frameworks, I chose Java Swing for all UI-related work.

The goal was to challenge myself and explore how far a traditional GUI framework can be pushed outside its intended scope.

## Features

- **MIDI Playback** – Load and play any standard `.mid` file  
- **Real-time Visualization** – Notes light up on a virtual piano as they are played  
- **Waterfall Animation** – Animated falling notes for easier practice  
- **Live MIDI Input** – Connect your own MIDI keyboard/controller to learn interactively  
- **Playback Controls** – Functional seek bar with full playback control
- **Dragging** - You can drag the animation up and down to traverse the visualization
- **Hand assignment** - You can enter the "Assign Hands" mode, where you can mark hands as left or right by clicking left or right respectively
- **Practice** - You can practice the pieces at your own pace, if you have created a hand assignment for a piece you can practice the parts seperately

---

## Getting Started

This application is self-contained and does not require you to install Java separately.

1.  Go to the [**Releases Page**](https://github.com/Tbence132545/Melodigram/releases).
2.  Download the installer for your operating system from the latest release.
3.  Run the executable from inside the folder
   
**The operating system might mark it as dangerous, I just didn't license it**


---

## Building from Source (for Developers)

If you want to build the project yourself, follow these steps.

### Prerequisites

* JDK 23 or newer.
* Git.

### Installation

1.  **Clone the repository:**
    ```sh
    git clone https://github.com/Tbence132545/Melodigram.git
    ```
2.  **Navigate to the project directory:**
    ```sh
    cd Melodigram
    ```
3.  **Build the project using the Gradle wrapper:**
    * On Windows:
        ```sh
        .\gradlew build
        ```
    * On Mac/Linux:
        ```sh
        ./gradlew build
        ```
4.  **Package the application:**
    After building, you can create the runnable application using the `jpackage` command:
    ```cmd
    jpackage --type app-image --name "Melodigram" --input "build/libs" --main-jar "Melodigram.jar" --main-class "com.Tbence132545.Melodigram.Main" --dest "release"
    ```

---
## Contribution

Feel free to address the issues, fix bugs, or extend the project with your ideas.  
You can do this by forking the repository, committing your changes, and submitting a pull request (PR) by following these steps:

1. Fork the repository to your GitHub account.  
2. Clone your fork locally.  
3. Create a new branch for your feature or bug fix.  
4. Make your changes and commit them with clear messages.  
5. Push your branch to your fork.  
6. Open a pull request to the original repository, describing your changes in detail.  

I appreciate your contributions and will review your PR as soon as possible!

## Technologies Used

* [Java](https://www.java.com/)
* [Java Swing](https://docs.oracle.com/javase/tutorial/uiswing/) (for the graphical user interface)
* [Gradle](https://gradle.org/) (for dependency management and building)

---

## License

This project is distributed under the MIT License. See the `LICENSE` file for more information.
