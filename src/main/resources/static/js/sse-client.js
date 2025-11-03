let sseEventSource = null;

function sseConnect() {
    if (sseEventSource) {
        addSseMessage('Already connected', 'system');
        return;
    }

    sseEventSource = new EventSource('/sse/response');

    sseEventSource.onopen = function(event) {
        updateSseStatus('Connected', 'connected');
        addSseMessage('Connected to SSE', 'system');
        document.getElementById('sse-connect').disabled = true;
        document.getElementById('sse-disconnect').disabled = false;
    };

    sseEventSource.onmessage = function(event) {
        const data = JSON.parse(event.data);
        addSseMessage(JSON.stringify(data, null, 2), 'received');
    };

    sseEventSource.onerror = function(error) {
        console.error('SSE Error:', error);
        addSseMessage('Connection error', 'error');

        if (sseEventSource.readyState === EventSource.CLOSED) {
            updateSseStatus('Disconnected', 'disconnected');
            document.getElementById('sse-connect').disabled = false;
            document.getElementById('sse-disconnect').disabled = true;
            sseEventSource = null;
        }
    };
}

function sseDisconnect() {
    if (sseEventSource) {
        sseEventSource.close();
        sseEventSource = null;
    }

    updateSseStatus('Disconnected', 'disconnected');
    document.getElementById('sse-connect').disabled = false;
    document.getElementById('sse-disconnect').disabled = true;
    addSseMessage('Disconnected', 'system');
}

function sseTriggerBroadcast() {
    // POST /api/broadcast - 서버가 Message 객체를 생성해서 브로드캐스트
    fetch('/api/broadcast', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
        .then(response => response.json())
        .then(data => {
            addSseMessage('Broadcast triggered: ' + JSON.stringify(data, null, 2), 'sent');
        })
        .catch(error => {
            addSseMessage('Error: ' + error.message, 'error');
        });
}

function updateSseStatus(text, className) {
    const statusEl = document.getElementById('sse-status');
    statusEl.textContent = text;
    statusEl.className = className;
}

function addSseMessage(content, type) {
    const messagesDiv = document.getElementById('sse-messages');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message';

    const time = new Date().toLocaleTimeString();
    messageDiv.innerHTML = `
        <div class="time">[${time}] ${type}</div>
        <div class="content">${content}</div>
    `;

    messagesDiv.appendChild(messageDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}
