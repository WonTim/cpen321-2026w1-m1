import { createServer } from 'node:http';
import { createApp } from './app';
import { env } from './config/env';
import { attachPixelRelay } from './pixelRelay';

const app = createApp();
const server = createServer(app);
attachPixelRelay(server);

server.listen(env.port, () => {
  console.log(`Server listening on port ${env.port}`);
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    server.close(() => {
      process.exit(0);
    });
  });
}
