from flask import Flask, jsonify
from datetime import datetime

app = Flask(__name__)

@app.route('/')
def home():
    return 'Estufa online'

@app.route('/status')
def status():
    return jsonify({
        'estado': 'online',
        'hora': datetime.now().isoformat()
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
