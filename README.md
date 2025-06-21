# Demo Video



https://github.com/user-attachments/assets/0d1a2fec-784a-46f8-97fe-ae41e956497c




# BLE Sine Wave Data Collection and Visualization System

This project demonstrates a system for generating sine wave data via a Bluetooth Low Energy (BLE) Android application, receiving it with another BLE Android application, logging it to a file, and then uploading this file to a Python Flask server for storage and visualization.

## System Components

1.  **`BLEGATTSERVER` (Android App)**:
    *   Acts as a BLE GATT Peripheral/Server.
    *   Generates sine wave data.
    *   Advertises a specific BLE service and characteristic.
    *   Sends the sine wave data to connected clients via GATT notifications.

2.  **`blereceiver` (Android App)**:
    *   Acts as a BLE GATT Central/Client.
    *   Scans for the `BLEGATTSERVER`.
    *   Connects to the server and subscribes to notifications for the sine wave data.
    *   Logs timestamped data to a local file on the Android device.
    *   Uploads this log file to the Python backend server via HTTP POST.

3.  **`server.py` (Python Flask Application)**:
    *   A web server that listens for file uploads.
    *   Saves the uploaded log file from the `blereceiver` app into an `uploads/` directory.
    *   Parses the data and uses `matplotlib` to generate and display a plot of the sine wave.

## Data Flow

1.  **Data Generation**: `BLEGATTSERVER` generates float values representing a sine wave.
2.  **BLE Transmission**: Data is sent as GATT notifications over Bluetooth to `blereceiver`.
3.  **Local Logging**: `blereceiver` receives data, timestamps it (format: `HH:mm:ss.SSS,value`), and saves it to a local text file.
4.  **File Upload**: When collection stops, `blereceiver` sends the text file to `server.py` via HTTP POST to its `/upload` endpoint. The file is sent with the name "log.txt" in the multipart request.
5.  **Server-Side Storage**: `server.py` saves the received file as `uploads/log.txt`.
6.  **Data Visualization**: `server.py` parses `uploads/log.txt` and attempts to plot the sine wave data using `matplotlib`.

## Setup and Usage

### Prerequisites

*   **Android Development**: Android Studio for building and running the Android apps.
*   **Python**: Python 3.x for the server.
*   **Hardware**:
    *   Two Android devices with Bluetooth Low Energy support. One to run `BLEGATTSERVER` and another for `blereceiver`.
    *   A computer to run the Python `server.py`. This computer needs to be on the same network as the `blereceiver` Android device for file upload.

### 1. Python Server (`server.py`)

1.  **Navigate to the root directory of the project.**
2.  **Create `uploads` directory (if it doesn't exist):**
    ```bash
    mkdir uploads
    ```
3.  **Install dependencies:**
    ```bash
    pip install Flask matplotlib
    ```
4.  **Run the server:**
    ```bash
    python server.py
    ```
    The server will start on `http://0.0.0.0:5000`. Note the IP address of this machine.

### 2. `BLEGATTSERVER` Android App

1.  **Open the `BLEGATTSERVER` project** in Android Studio (located in the `BLEGATTSERVER/` directory).
2.  **Build and run the app** on an Android device.
3.  The app will automatically start advertising as a BLE GATT server upon launch if permissions are granted.
    *   It requests Bluetooth and Location permissions. Ensure these are granted.
    *   Logs on the app screen will indicate its status.

### 3. `blereceiver` Android App

1.  **Open the `blereceiver` project** in Android Studio (located in the `blereceiver/` directory).
2.  **Configure Server IP**:
    *   Open `blereceiver/app/src/main/res/values/strings.xml`.
    *   Change `<string name="server_ip">192.168.18.32</string>` to the actual IP address of the machine running `server.py`.
    *   Open `blereceiver/app/src/main/res/xml/network_security_config.xml`.
    *   Update `<domain includeSubdomains="false">192.168.18.32</domain>` to the same IP address if it's different, to allow cleartext HTTP traffic to your server.
3.  **Build and run the app** on a *different* Android device than the one running `BLEGATTSERVER`.
4.  **Usage**:
    *   The app will request Bluetooth and Location permissions. Ensure these are granted.
    *   Enter a filename (e.g., `my_sine_data`) in the text field.
    *   Tap "Start Logging". The app will ask you to choose a location and confirm the filename for the log file.
    *   It will then scan for the `BLEGATTSERVER`.
    *   Once connected, it will start receiving data and logging it to the screen and the file.
    *   Tap "Stop Logging". The app will stop data collection and attempt to upload the saved log file to the Python server.
    *   Check the `blereceiver` app logs and the Python server console for status messages. If successful, a plot window should appear on the machine running `server.py`.

## Files and Directories

*   **`BLEGATTSERVER/`**: Android project for the BLE GATT server application.
    *   `app/src/main/java/com/example/blegattserver/MainActivity.kt`: Core logic for the GATT server, data generation, and advertising.
*   **`blereceiver/`**: Android project for the BLE client application.
    *   `app/src/main/java/com/example/blereceiver/MainActivity.kt`: Core logic for BLE scanning, connection, data reception, local file logging, and HTTP upload.
    *   `app/src/main/res/values/strings.xml`: Contains the server IP configuration.
    *   `app/src/main/res/xml/network_security_config.xml`: Network security configuration for allowing HTTP traffic to the server.
*   **`server.py`**: Python Flask web server script.
*   **`uploads/`**: Directory where `server.py` saves uploaded log files.
    *   `data.txt`, `log.txt`: Example log files that might have been previously uploaded.
*   **`server_app-debug.apk`**: Pre-compiled debug APK for the `BLEGATTSERVER` app.
*   **`reciver_app-debug.apk`**: Pre-compiled debug APK for the `blereceiver` app.
*   **`README.md`**: This file.

## How the Code Works

### `BLEGATTSERVER` (`MainActivity.kt`)
*   Initializes Bluetooth adapter and advertiser.
*   Sets up a GATT server with a custom service (`12345678-1234-5678-1234-56789abcdef0`) and characteristic (`abcdef01-1234-5678-1234-56789abcdef0`).
*   The characteristic has `PROPERTY_NOTIFY`, allowing the server to send data updates to subscribed clients.
*   Starts advertising the service.
*   When a client connects, a timer generates sine wave values at 100 Hz. These float values are converted to bytes and sent via `notifyCharacteristicChanged()`.

### `blereceiver` (`MainActivity.kt`)
*   Initializes Bluetooth adapter and scanner.
*   On "Start Logging":
    *   Prompts user to create/select a log file.
    *   Scans for BLE devices advertising the specific `SERVICE_UUID` used by `BLEGATTSERVER`.
    *   Connects to the found device.
    *   Discovers services and subscribes to notifications on the specific `CHARACTERISTIC_UUID`.
*   `onCharacteristicChanged()`:
    *   Receives byte data, converts it to a float (sine value).
    *   Formats a string with timestamp and value: `HH:mm:ss.SSS,sineValue`.
    *   Appends this string to the on-screen log and the local file.
*   On "Stop Logging":
    *   Calls `sendFileToServer()`.
    *   This function creates an HTTP POST request (`multipart/form-data`) to `http://<server_ip>:5000/upload`.
    *   The content of the local log file is streamed in the request body. The file in the form data is named "file" with filename "log.txt".

### `server.py`
*   Uses Flask to create a web server.
*   The `@app.route('/upload', methods=['POST'])` decorator defines the endpoint for file uploads.
*   It expects a file named `file` in the request.
*   Saves the uploaded file to the `uploads/` directory (e.g., `uploads/log.txt`).
*   Calls `plot_sine_wave()`:
    *   Reads the saved file line by line.
    *   Parses lines of the format `timestamp,value`, extracting the `value`.
    *   Uses `matplotlib.pyplot` to plot these values against their sample index.
    *   `plt.show()` attempts to display the plot.

## Notes
*   The `BLEGATTSERVER` sends raw float data. The `blereceiver` timestamps it upon reception.
*   The filename of the log file on the `blereceiver` device can be chosen by the user, but when uploaded, the filename in the HTTP request is hardcoded to "log.txt". Thus, `server.py` will always save it as `uploads/log.txt`, potentially overwriting previous uploads if not renamed.
*   The Python server's `plt.show()` requires a graphical environment to display plots. If running on a headless server, you might want to modify it to save the plot to a file instead (e.g., using `plt.savefig('plot.png')`).
*   Ensure the Android device running `blereceiver` and the computer running `server.py` are on the same network and can reach each other via the configured IP address. Firewalls might need to be configured to allow traffic on port 5000.
