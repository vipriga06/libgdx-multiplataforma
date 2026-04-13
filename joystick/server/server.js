const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');

const app = express();
const httpServer = http.createServer(app);
const wss = new WebSocket.Server({ server: httpServer });

const port = 8888;
const clients = new Map();
const broadcastRateHz = 30;
const broadcastIntervalMs = Math.floor(1000 / broadcastRateHz);

app.use(express.static('public'));
app.get('/', (_req, res) => {
  res.send('IETI Game WebSocket Server');
});

wss.on('connection', (ws) => {
  const userId = uuidv4();
  const now = Date.now();

  const playerState = {
    id: userId,
    x: 0,
    y: 0,
    axisX: 0,
    axisY: 0,
    direction: 'IDLE',
    moving: false,
    touch: false,
    keyboard: false,
    sequence: 0,
    clientTimeMs: now,
    serverTimeMs: now
  };

  clients.set(userId, {
    id: userId,
    ws,
    state: playerState,
    lastPrintMs: 0
  });

  console.log(`[WS] Nueva conexion ${userId}`);

  ws.send(JSON.stringify({
    type: 'hello',
    id: userId,
    serverTimeMs: now
  }));

  ws.on('message', (data) => {
    try {
      const message = JSON.parse(data.toString());
      if (message.type !== 'player_state') {
        return;
      }

      const client = clients.get(userId);
      if (!client) {
        return;
      }

      const serverTimeMs = Date.now();
      client.state = {
        ...client.state,
        x: Number(message.x) || 0,
        y: Number(message.y) || 0,
        axisX: Number(message.axisX) || 0,
        axisY: Number(message.axisY) || 0,
        direction: typeof message.direction === 'string' ? message.direction : 'IDLE',
        moving: Boolean(message.moving),
        touch: Boolean(message.touch),
        keyboard: Boolean(message.keyboard),
        sequence: Number(message.sequence) || 0,
        clientTimeMs: Number(message.clientTimeMs) || 0,
        serverTimeMs
      };

      if (serverTimeMs - client.lastPrintMs >= 120) {
        client.lastPrintMs = serverTimeMs;
        console.log(
          `[POS] ${userId.slice(0, 8)} x=${client.state.x.toFixed(1)} y=${client.state.y.toFixed(1)} dir=${client.state.direction} moving=${client.state.moving} touch=${client.state.touch} keyboard=${client.state.keyboard}\n`
        );
      }
    } catch (error) {
      console.error(`[WS] Error parseando mensaje de ${userId}:`, error.message);
    }
  });

  ws.on('close', () => {
    clients.delete(userId);
    console.log(`[WS] Desconexion ${userId}`);
    broadcast({
      type: 'player_left',
      id: userId,
      serverTimeMs: Date.now()
    });
  });

  ws.on('error', (error) => {
    console.error(`[WS] Error en ${userId}:`, error.message);
  });
});

function broadcast(payload) {
  const json = JSON.stringify(payload);
  for (const client of clients.values()) {
    if (client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(json);
    }
  }
}

setInterval(() => {
  const now = Date.now();
  const players = [];

  for (const client of clients.values()) {
    players.push(client.state);
  }

  broadcast({
    type: 'state',
    serverTimeMs: now,
    players
  });
}, broadcastIntervalMs);

httpServer.listen(port, () => {
  console.log(`WebSocket server escuchando en http://localhost:${port}`);
});
