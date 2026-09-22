import type { Server } from 'node:http';
import { WebSocket, WebSocketServer } from 'ws';

const COURSE_SOCKET_URL = 'wss://8.229.22.124';
export const PIXEL_SOCKET_PATH = '/ws/pixels';

export function attachPixelRelay(server: Server): WebSocketServer {
  const socketServer = new WebSocketServer({ noServer: true });

  server.on('upgrade', (request, socket, head) => {
    const requestUrl = new URL(request.url ?? '/', 'http://localhost');
    if (requestUrl.pathname !== PIXEL_SOCKET_PATH) {
      socket.destroy();
      return;
    }

    socketServer.handleUpgrade(request, socket, head, (client) => {
      socketServer.emit('connection', client, request);
    });
  });

  socketServer.on('connection', (client) => {
    const upstream = new WebSocket(COURSE_SOCKET_URL, {
      rejectUnauthorized: false,
    });

    upstream.on('message', (payload, isBinary) => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(payload, { binary: isBinary });
      }
    });

    upstream.on('error', () => {
      if (client.readyState === WebSocket.OPEN) {
        client.close(1011, 'Course WebSocket unavailable');
      }
    });

    client.on('close', () => {
      if (upstream.readyState === WebSocket.OPEN || upstream.readyState === WebSocket.CONNECTING) {
        upstream.close();
      }
    });
  });

  return socketServer;
}