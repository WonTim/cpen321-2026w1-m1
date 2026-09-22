import 'dotenv/config';

const rawPort = process.env.PORT;
const port =
  rawPort === undefined || rawPort === ''
    ? 3000
    : Number.parseInt(rawPort, 10);

if (Number.isNaN(port) || port < 1 || port > 65535) {
  throw new Error(`Invalid PORT: ${rawPort}`);
}

export const env = {
  port,
  serverPublicIp: process.env.SERVER_PUBLIC_IP ?? 'Unconfigured',
  ownerFirstName: process.env.OWNER_FIRST_NAME ?? 'Unconfigured',
  ownerLastName: process.env.OWNER_LAST_NAME ?? 'Unconfigured',
} as const;
