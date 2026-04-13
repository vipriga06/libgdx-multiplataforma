# Joystick WebSocket Server

Servidor WebSocket para recibir estado del jugador desde libGDX y redistribuir snapshots suaves en tiempo real.

## Requisitos

- Node.js 18+

## Instalacion

```bash
npm install
```

## Ejecucion

```bash
npm start
```

El servidor queda en `ws://localhost:8888`.

## Protocolo

### Cliente -> Servidor

```json
{
  "type": "player_state",
  "sequence": 101,
  "clientTimeMs": 1710000000000,
  "x": 320.5,
  "y": 120.2,
  "axisX": 1.0,
  "axisY": 0.0,
  "direction": "RIGHT",
  "moving": true,
  "touch": false,
  "keyboard": true
}
```

### Servidor -> Cliente

`hello` al conectar:

```json
{
  "type": "hello",
  "id": "uuid",
  "serverTimeMs": 1710000000000
}
```

`state` a 30 Hz:

```json
{
  "type": "state",
  "serverTimeMs": 1710000000100,
  "players": [
    {
      "id": "uuid",
      "x": 320.5,
      "y": 120.2,
      "axisX": 1,
      "axisY": 0,
      "direction": "RIGHT",
      "moving": true,
      "touch": false,
      "keyboard": true,
      "sequence": 101,
      "clientTimeMs": 1710000000000,
      "serverTimeMs": 1710000000099
    }
  ]
}
```

`player_left` cuando se desconecta:

```json
{
  "type": "player_left",
  "id": "uuid",
  "serverTimeMs": 1710000000500
}
```
