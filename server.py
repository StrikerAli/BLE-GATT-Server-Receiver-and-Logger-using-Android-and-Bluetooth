from flask import Flask, request
import os
import matplotlib.pyplot as plt

app = Flask(__name__)
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@app.route('/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        return 'No file part', 400

    file = request.files['file']
    filepath = os.path.join(UPLOAD_FOLDER, file.filename)
    file.save(filepath)
    print(f"Received file: {file.filename}")

    try:
        plot_sine_wave(filepath)
    except Exception as e:
        print(f"Error plotting: {e}")
        return 'File uploaded, but plotting failed', 500

    return 'File uploaded and plotted successfully', 200

def plot_sine_wave(filepath):
    timestamps = []
    values = []

    with open(filepath, 'r') as f:
        for line in f:
            if ',' in line:
                time_str, value_str = line.strip().split(',')
                try:
                    value = float(value_str)
                    timestamps.append(time_str)
                    values.append(value)
                except ValueError:
                    continue  # skip malformed lines

    if not values:
        print("No valid data to plot.")
        return

    # Plotting
    plt.figure(figsize=(10, 4))
    plt.plot(values, label='Sine Wave')
    plt.title("Received Sine Wave Data")
    plt.xlabel("Sample Index")
    plt.ylabel("Amplitude")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.show()

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
