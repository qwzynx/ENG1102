# ENG1102: The Productivity FocusBox

## Project Overview
This project was developed for **ENG1102**, focusing on creating a physical solution to help students manage their study time and reduce phone distractions. The **FocusBox** is an interactive study timer that ensures your phone stays put while you work, rewarding you with breaks and suggesting productive tasks when you need them.

![Final Product](.image04.jpg)

## The Design Process
Our team followed a rigorous engineering design process to bring the FocusBox to life.

### Diverging and Converging Thinking
We utilized **diverging and converging thinking** at multiple stages of the project:
*   **Diverging:** We brainstormed a wide range of solutions for student productivity, from digital apps to complex mechanical lockboxes.
*   **Converging:** We evaluated our ideas against constraints like cost, ease of use, and hardware limitations to select the best possible concept—a smart, sensor-monitored focus container.

### Iterative Planning
The project was planned multiple times. Each iteration refined our requirements, logic flows, and hardware specifications. We transitioned from simple state diagrams to a complex state machine that handles phone detection, user presence, and task management seamlessly.

## Prototyping and Development
We didn't just build it once; we prototyped the FocusBox multiple times to ensure durability and functionality.

*   **Hardware Integration:** Using Java and the `firmata4j` library, we interfaced an Arduino-compatible device with various sensors and actuators.
*   **3D Printing:** The enclosure went through several design iterations, optimized for 3D printing to house the electronics securely while providing a functional "phone slot."
*   **Iterative Testing:** Each prototype was tested for light-leakage (phone detection) and sensor accuracy, leading to the final robust version.

## Key Features
*   **Phone Lock-in:** Uses a light sensor to detect if the phone is removed prematurely, triggering a flashing alarm and adding time penalties.
*   **Presence Detection:** A PIR motion sensor detects if the user is away and automatically pauses the timer.
*   **Productive Task Suggestions:** Randomly proposes "active breaks" (like stretching or drinking water) to prevent burnout.
*   **Customizable Sessions:** A potentiometer allows users to set their desired study duration.
*   **OLED Interface:** Clear visual feedback for time remaining, current state, and task prompts.

## Hardware Components
*   **Microcontroller:** Arduino-compatible board
*   **Display:** SSD1306 OLED (128x64)
*   **Sensors:** PIR Motion Sensor, Light Sensor (LDR), Potentiometer
*   **Actuators:** LED, Buzzer
*   **Enclosure:** Custom 3D-printed chassis

## Setup
1. Connect the hardware via USB (Default: `COM3` on Windows, `/dev/ttyUSB0` on Linux).
2. Ensure the Firmata firmware is loaded on your microcontroller.
3. Run the `App.java` file.
4. Use the potentiometer to set your time and press the button to begin!

---
*Developed as part of the ENG1102 Engineering Design curriculum.*
