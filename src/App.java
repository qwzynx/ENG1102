import org.firmata4j.firmata.FirmataDevice;
import org.firmata4j.ssd1306.SSD1306;
import org.firmata4j.ssd1306.MonochromeCanvas;
import org.firmata4j.Pin;
import org.firmata4j.I2CDevice;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;
//Line 245 to re-enable buzzer
public class App {
    static final int A0 = 14;
    static final int A2 = 16; // Standard analog pin 6 on Uno/Nano
    static final int D4 = 4;
    static final int D5 = 5;  // Buzzer pin
    static final int D6 = 6;
    static final int D7 = 7;
    static final byte I2C0 = 0x3C;

    static final String[] productiveTasks = {
            "Drink a full glass of cold water",
            "Walk outside for five minutes",
            "Clear your physical workspace of trash",
            "Do ten jumping jacks or a quick stretch",
            "Eat a high-protein snack like nuts",
            "Set a five-minute timer to meditate",
            "Refill your water bottle",
            "Write down your top three priorities",
            "Put your phone in another room",
            "Step away from all screens for a moment",
            "Take five deep, controlled breaths",
            "Organize your digital desktop folders",
            "Change into fresh, comfortable clothes",
            "Listen to one upbeat song",
            "Wash your face with cold water"
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        String myUSBPort = System.getProperty("os.name").toLowerCase().contains("win")
                ? "COM3"
                : "/dev/ttyUSB0";
        var device = new FirmataDevice(myUSBPort);
        device.start();
        device.ensureInitializationIsDone();

        Pin potPin = device.getPin(A0);
        potPin.setMode(Pin.Mode.ANALOG);

        Pin lightPin = device.getPin(A2);
        lightPin.setMode(Pin.Mode.ANALOG);

        Pin ledPin = device.getPin(D4);
        ledPin.setMode(Pin.Mode.OUTPUT);

        Pin buzzerPin = device.getPin(D5);
        buzzerPin.setMode(Pin.Mode.OUTPUT);

        Pin buttonPin = device.getPin(D6);
        buttonPin.setMode(Pin.Mode.INPUT);

        Pin pirPin = device.getPin(D7);
        pirPin.setMode(Pin.Mode.INPUT);

        I2CDevice i2cDevice = device.getI2CDevice(I2C0);
        SSD1306 display = new SSD1306(i2cDevice, SSD1306.Size.SSD1306_128_64);
        display.init();

        Timer timer = new Timer();
        var task = new StudyTimer(display, potPin, lightPin, pirPin, ledPin, buzzerPin, timer);
        timer.schedule(task, 0, 1000);

        var buttonTask = new ButtonPresser(buttonPin, task);
        timer.schedule(buttonTask, 0, 50);
    }
}

class StudyTimer extends TimerTask {
    private final SSD1306 display;
    private final Pin potPin;
    private final Pin lightPin;
    private final Pin pirPin;
    private final Pin ledPin;
    private final Pin buzzerPin;
    private final Timer timer;

    // State Machine to handle complex transitions cleanly
    enum State { SETUP, WAITING_PHONE, STUDYING, ALARM, TASK_PROPOSED, REWARD_ASK, REWARD_TIME, AWAY, SHUTOFF }
    static State currentState = State.SETUP;
    private State previousState = State.SETUP; // To return from AWAY

    private String productiveText = "";
    private int time = 0;
    private int initialTime = 0;
    private int rewardTime = 0;
    private final Random rand = new Random();

    // Threshold for light sensor (Adjust this based on your room/box darkness)
    private final int LIGHT_THRESHOLD = 300;

    // Motion Tracking
    private int noMotionSeconds = 0;

    public StudyTimer(SSD1306 display, Pin potPin, Pin lightPin, Pin pirPin, Pin ledPin, Pin buzzerPin, Timer timer) {
        this.display = display;
        this.potPin = potPin;
        this.lightPin = lightPin;
        this.pirPin = pirPin;
        this.ledPin = ledPin;
        this.buzzerPin = buzzerPin;
        this.timer = timer;
    }

    public void buttonPressed() {
        switch (currentState) {
            case SETUP:
                // Start process, wait for phone
                currentState = State.WAITING_PHONE;
                break;
            case STUDYING:
                // Propose a task
                productiveText = App.productiveTasks[rand.nextInt(App.productiveTasks.length)];
                currentState = State.TASK_PROPOSED;
                break;
            case TASK_PROPOSED:
                // Potentiometer decides Yes (Done) or No (Skip)
                if (potPin.getValue() < 1024 / 2) {
                    currentState = State.REWARD_ASK; // Task finished
                } else {
                    currentState = State.STUDYING; // Skipped task
                }
                break;
            case REWARD_ASK:
                // Potentiometer decides if they want phone time
                if (potPin.getValue() < 1024 / 2) {
                    rewardTime = 300; // 5 minutes
                    currentState = State.REWARD_TIME;
                } else {
                    currentState = State.STUDYING; // Declined phone time
                }
                break;
            case REWARD_TIME:
                // End break early, reset reward time, and force putting the phone back
                rewardTime = 0;
                currentState = State.WAITING_PHONE;
                break;
            case SHUTOFF:
                if (potPin.getValue() < 1024 / 2) {
                    time = 0;
                    initialTime = 0; // 5 minutes
                    currentState = State.SETUP;
                } else {
                    try {
                        // 1. Turn off all outputs so nothing gets stuck on
                        ledPin.setValue(0);
                        buzzerPin.setValue(0);

                        // 2. Say goodbye and clear the screen completely
                        display.getCanvas().clear();
                        display.getCanvas().drawString(40, 30, "Goodbye!");
                        display.display();

                        Thread.sleep(1000); // Show the message for 1 second

                        display.getCanvas().clear();
                        display.display(); // Push the blank canvas to the OLED

                        // 3. Stop the timer and kill the Java program
                        timer.cancel();
                        System.exit(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    @Override
    public void run() {
        try {
            display.getCanvas().clear();
            long lightLevel = lightPin.getValue();

            // Handle PIR sensor globally (unless in setup, reward, or alarm)
            if (currentState == State.STUDYING || currentState == State.AWAY) {
                if (pirPin.getValue() > 0) {
                    noMotionSeconds = 0;
                    if (currentState == State.AWAY) {
                        currentState = previousState;
                        ledPin.setValue(0);
                    }
                } else {
                    System.out.println(noMotionSeconds);
                    noMotionSeconds++;
                    if (noMotionSeconds >= 30 && currentState != State.AWAY) {
                        previousState = currentState;
                        currentState = State.AWAY;
                    }
                }
            }

            switch (currentState) {
                case SETUP:
                    int potValue = (int) potPin.getValue();
                    int section = Math.min(potValue / (1024 / 6), 5) + 1;
                    int rectWidth = (120 / 6) * section;
                    initialTime = (10 * section) * 60;
                    time = initialTime;

                    display.getCanvas().drawString(0, 0, "Study Time: " + initialTime / 60 + "min");
                    display.getCanvas().drawString(0, 40, "Press button to begin");
                    display.getCanvas().fillRect(0, 20, rectWidth, 10);
                    break;

                case WAITING_PHONE:
                    display.getCanvas().drawString(0, 20, "Please place phone");
                    display.getCanvas().drawString(0, 35, "inside the box...");

                    if (lightLevel < LIGHT_THRESHOLD) {
                        currentState = State.STUDYING; // Phone is in!
                    }
                    break;

                case STUDYING:
                    ledPin.setValue(0);
                    buzzerPin.setValue(0);

                    // Phone picked up check
                    if (lightLevel >= LIGHT_THRESHOLD) {
                        currentState = State.ALARM;
                        break;
                    }

                    int studyRectWidth = 128 * time / initialTime;
                    display.getCanvas().drawString(0, 0, "Time Left: " + Math.max(time / 60, 0) + ":" + String.format("%02d", time % 60));
                    display.getCanvas().fillRect(4, 20, studyRectWidth, 10, MonochromeCanvas.Color.BRIGHT);

                    time--;
                    if (time <= 0) {
                        currentState = State.SETUP; // Done!
                    }
                    break;

                case ALARM:
                    display.getCanvas().drawString(0, 20, "! PUT PHONE BACK !");
                    display.getCanvas().drawString(0, 30, "Each Second is 5 Seconds Extra");
                    time = time+5;

                    // Flash LED and Buzzer
                    long toggle = ledPin.getValue() == 0 ? 1 : 0;
                    ledPin.setValue(toggle);
                    buzzerPin.setValue(toggle);

                    if (lightLevel < LIGHT_THRESHOLD) {
                        // Phone put back
                        ledPin.setValue(0);
                        buzzerPin.setValue(0);
                        currentState = State.STUDYING;
                    }
                    break;

                case TASK_PROPOSED:
                    display.getCanvas().drawString(0, 0, productiveText);
                    drawChoice("Done", "Skip");
                    break;

                case REWARD_ASK:
                    display.getCanvas().drawString(0, 0, "Task Complete!");
                    display.getCanvas().drawString(0, 15, "Use phone for 5 min?");
                    drawChoice("Yes", "No");
                    break;

                case REWARD_TIME:
                    display.getCanvas().drawString(0, 0, "Phone Time: " + Math.max(rewardTime / 60, 0) + ":" + String.format("%02d", rewardTime % 60));
                    int rewardRectWidth = 128 * rewardTime / 300;
                    display.getCanvas().fillRect(4, 20, rewardRectWidth, 10, MonochromeCanvas.Color.BRIGHT);

                    rewardTime--;
                    if (rewardTime <= 0) {
                        currentState = State.WAITING_PHONE; // Force putting phone back
                    }
                    break;

                case AWAY:
                    display.getCanvas().drawString(0, 0, "You are away.");
                    display.getCanvas().drawString(0, 15, "Timer paused.");
                    ledPin.setValue(ledPin.getValue() == 0 ? 1 : 0);
                    break;

                case SHUTOFF:
                    drawChoice("Restart", "Shutoff");
            }
            display.display();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawChoice(String yesLabel, String noLabel) {
        int yesX = 65;
        int yesLength = yesLabel.length();
        display.getCanvas().drawString(yesX, 40, yesLabel);
        int noX = 10;
        int noLength = noLabel.length();
        display.getCanvas().drawString(noX, 40, noLabel);

        if (potPin.getValue() < 1024 / 2) {
            display.getCanvas().drawHorizontalLine(yesX-2, 50, yesLength*6+2, MonochromeCanvas.Color.BRIGHT);
        } else {
            display.getCanvas().drawHorizontalLine(noX-2, 50, noLength*6+2, MonochromeCanvas.Color.BRIGHT);
        }
    }
}

class ButtonPresser extends TimerTask {
    private final Pin buttonPin;
    private final StudyTimer task;
    private boolean isPressed = false;
    private long pressStartTime = 0; // Tracks when the button was first pressed

    public ButtonPresser(Pin buttonPin, StudyTimer task) {
        this.buttonPin = buttonPin;
        this.task = task;
    }

    @Override
    public void run() {
        try {
            if (buttonPin.getValue() > 0) {
                if (!isPressed) {
                    // The exact moment the button goes down
                    isPressed = true;
                    pressStartTime = System.currentTimeMillis();
                } else {
                    // The button is being held down. Check how long it's been held.
                    if (System.currentTimeMillis() - pressStartTime > 2000) {
                        // 2000 milliseconds = 2 seconds long press detected
                        StudyTimer.currentState = StudyTimer.State.SHUTOFF;
                    }
                }
            } else if (buttonPin.getValue() == 0 && isPressed) {
                // The button was released
                isPressed = false;
                long pressDuration = System.currentTimeMillis() - pressStartTime;

                // If it was held for less than 2 seconds, treat it as a normal click
                if (pressDuration < 2000) {
                    task.buttonPressed();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}