let stompClient = null;

function wsConnect() {
    if (stompClient && stompClient.connected) {
        addWsMessage('Already connected', 'system');
        return;
    }

    const socket = new SockJS('/ws');
    stompClient = StompJs.Stomp.over(socket);

    stompClient.debug = function(str) {
        console.log('STOMP: ' + str);
    };

    stompClient.connect({}, function(frame) {
        updateWsStatus('Connected', 'connected');
        addWsMessage('Connected to WebSocket', 'system');
        document.getElementById('ws-connect').disabled = true;
        document.getElementById('ws-disconnect').disabled = false;

        // Subscribe to /topic/response
        stompClient.subscribe('/topic/response', function(message) {
            const data = JSON.parse(message.body);
            addWsMessage(JSON.stringify(data, null, 2), 'received');
        });

    }, function(error) {
        updateWsStatus('Disconnected', 'disconnected');
        addWsMessage('Connection error: ' + error, 'error');
        document.getElementById('ws-connect').disabled = false;
        document.getElementById('ws-disconnect').disabled = true;
    });
}

function wsDisconnect() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect(function() {
            updateWsStatus('Disconnected', 'disconnected');
            addWsMessage('Disconnected', 'system');
        });
    }

    stompClient = null;
    document.getElementById('ws-connect').disabled = false;
    document.getElementById('ws-disconnect').disabled = true;
}

function wsTriggerBroadcast() {
    if (!stompClient || !stompClient.connected) {
        alert('Not connected');
        return;
    }

    // Send to /app/broadcast - 서버가 Message 객체를 생성해서 브로드캐스트
    stompClient.send('/app/broadcast', {}, 'trigger');
    addWsMessage('Broadcast triggered', 'sent');
}

function updateWsStatus(text, className) {
    const statusEl = document.getElementById('ws-status');
    statusEl.textContent = text;
    statusEl.className = className;
}

function addWsMessage(content, type) {
    const messagesDiv = document.getElementById('ws-messages');
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
