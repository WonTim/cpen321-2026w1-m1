import express, { type Express } from 'express';
import { env } from './config/env';

function clientIp(request: express.Request): string {
  const forwardedFor = request.header('x-forwarded-for');
  return forwardedFor?.split(',')[0]?.trim() || request.socket.remoteAddress || 'unknown';
}

function formatServerTime(): string {
  const now = new Date();
  const offsetMinutes = -now.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? '+' : '-';
  const absoluteOffset = Math.abs(offsetMinutes);
  const hours = String(Math.floor(absoluteOffset / 60)).padStart(2, '0');
  const minutes = String(absoluteOffset % 60).padStart(2, '0');
  const time = now.toTimeString().slice(0, 8);
  return `${time} GMT${sign}${hours}:${minutes}`;
}

export function createApp(): Express {
  const app = express();

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.get('/api/server-ip', (_req, res) => {
    res.json({ serverIp: env.serverPublicIp });
  });

  app.get('/api/server-time', (_req, res) => {
    res.json({ serverTime: formatServerTime() });
  });

  app.get('/api/owner', (_req, res) => {
    res.json({ firstName: env.ownerFirstName, lastName: env.ownerLastName });
  });

  app.get('/api/client-ip', (req, res) => {
    res.json({ clientIp: clientIp(req) });
  });

  app.use((_req, res) => {
    res.status(404).json({ error: 'Not Found' });
  });

  return app;
}
