import base from '../../liana-web/vite.config.js';
import { fileURLToPath } from 'node:url';

const ports = { auth: 18081, facility: 18082, oms: 18083, dispatch: 18084,
  sorting: 18087, records: 18085, transport: 18086, postage: 18090 };

export default {
  ...base,
  root: fileURLToPath(new URL('../../liana-web', import.meta.url)),
  server: {
    host: '127.0.0.1', port: 15173, strictPort: true,
    proxy: Object.fromEntries(Object.entries(ports).map(([name, port]) => {
      const prefix = `/liana-${name}-service`;
      return [prefix, { target: `http://127.0.0.1:${port}`, changeOrigin: true,
        rewrite: path => path.slice(prefix.length) }];
    })),
  },
};
