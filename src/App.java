import org.firmata4j.firmata.FirmataDevice;
import org.firmata4j.ssd1306.SSD1306;
import org.firmata4j.ssd1306.MonochromeCanvas;
import org.firmata4j.Pin;
import org.firmata4j.I2CDevice;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

public class App {
    static final int A0 = 14;
    static final int D4 = 4;
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
        // Replace the hardcoded "COM3" with this to work on both:
        String myUSBPort = System.getProperty("os.name").toLowerCase().contains("win")
                ? "COM3"
                : "/dev/ttyUSB0";
        var device = new FirmataDevice(myUSBPort);
        device.start();
        device.ensureInitializationIsDone();

        Pin potPin = device.getPin(A0);
        potPin.setMode(Pin.Mode.ANALOG);

        Pin ledPin = device.getPin(D4);
        ledPin.setMode(Pin.Mode.OUTPUT);

        Pin buttonPin = device.getPin(D6);
        buttonPin.setMode(Pin.Mode.INPUT);

        Pin pirPin = device.getPin(D7);
        pirPin.setMode(Pin.Mode.INPUT);

        I2CDevice i2cDevice = device.getI2CDevice(I2C0);
        SSD1306 display = new SSD1306(i2cDevice, SSD1306.Size.SSD1306_128_64);
        display.init();

        Timer timer = new Timer();
        var task = new StudyTimer(display, potPin, pirPin, ledPin, timer);
        timer.schedule(task, 0, 1000);

        var buttonTask = new ButtonPresser(buttonPin, task);
        timer.schedule(buttonTask, 0, 50);
    }
}

class StudyTimer extends TimerTask {
    private final SSD1306 display;
    private final Pin potPin;
    private final Pin pirPin;
    private final Pin ledPin;
    private final Timer timer;

    private boolean timerRunning = false;
    private boolean productive = false;
    private String productiveText = "";
    private int time = 0;
    private int initialTime = 0;
    private final Random rand = new Random();

    // Motion Tracking
    private int noMotionSeconds = 0;
    private boolean awayAlertActive = false;

    public StudyTimer(SSD1306 display, Pin potPin, Pin pirPin, Pin ledPin, Timer timer) {
        this.display = display;
        this.potPin = potPin;
        this.pirPin = pirPin;
        this.ledPin = ledPin;
        this.timer = timer;
    }

    public void productiveToggle() {
        if (!timerRunning) { timerRunning = true; }
        else if (!productive) {
            productive = true;
            productiveText = App.productiveTasks[rand.nextInt(App.productiveTasks.length)];
        }
        else {
            productive = false;
            if (potPin.getValue() == 0) { initialTime = -1; }
            if (potPin.getValue() > 1024/2) { timerRunning = false; }
        }
    }

    @Override
    public void run() {
        try {
            display.getCanvas().clear();
            if (initialTime == -1) {
                display.getCanvas().clear();
                display.display();
                timer.cancel();
                System.exit(0);
            }
            if (awayAlertActive) {
                display.getCanvas().drawString(0, 0, "You are currently away, The timer is paused");

                ledPin.setValue(ledPin.getValue() == 0 ? 1 : 0);

                if (pirPin.getValue() > 0) {
                    awayAlertActive = false;
                }
            } else if (productive) {
                ledPin.setValue(0);
                display.getCanvas().drawString(0, 0, productiveText);

                int yesX = 65;
                display.getCanvas().drawString(yesX, 40, "Continue");

                int noX = 10;
                display.getCanvas().drawString(noX, 40, "Restart");

                if (potPin.getValue() < 1024/2) {
                    display.getCanvas().drawHorizontalLine(yesX, 50, 48, MonochromeCanvas.Color.BRIGHT);
                } else {
                    display.getCanvas().drawHorizontalLine(noX, 50, 42, MonochromeCanvas.Color.BRIGHT);
                }

            } else if (!timerRunning) {
                int potValue = (int) potPin.getValue();

                int section = Math.min(potValue / (1024 / 6), 5) + 1;

                int rectWidth = (120 / 6) * section;
                initialTime = (10 * section) * 60;

                // 3. Draw once
                display.getCanvas().drawString(0, 0, "Study Time: " + initialTime/60 + "min");
                display.getCanvas().fillRect(4, 20, rectWidth, 10, MonochromeCanvas.Color.BRIGHT);

                time = initialTime;
            } else {
                // Check PIR Sensor (REL pin is High when motion is detected)
                if (pirPin.getValue() > 0) {
                    noMotionSeconds = 0;
                    awayAlertActive = false;
                } else {
                    noMotionSeconds++;
                    System.out.println(noMotionSeconds);
                    if (noMotionSeconds >= 60 && !awayAlertActive) {
                        awayAlertActive = true;
                    }
                }
                int rectWidth = 128*time/initialTime;
                display.getCanvas().drawString(0, 0, "Time Left: " + Math.max(time/60, 0) + ":" + Math.round(time % 60.0));
                display.getCanvas().fillRect(4, 20, rectWidth, 10, MonochromeCanvas.Color.BRIGHT);

                time--;
                if (time == 0) {
                    timerRunning = !timerRunning;
                }
            }
            display.display();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ButtonPresser extends TimerTask {
    private final Pin buttonPin;
    private final StudyTimer task;
    private boolean isPressed = false;

    public ButtonPresser(Pin buttonPin, StudyTimer task) {
        this.buttonPin = buttonPin;
        this.task = task;
    }

    @Override
    public void run() {

        if (buttonPin.getValue() > 0 && !isPressed) {
            isPressed = true;
            task.productiveToggle();
        }
        else if (buttonPin.getValue() == 0) {
            isPressed = false;
        }
    }
}

