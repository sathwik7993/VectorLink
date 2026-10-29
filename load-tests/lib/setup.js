import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// The rate limiter keys on X-Forwarded-For, so each simulated client gets its own private IP.
// 10.x addresses also map to a fixed geo location in the app, so no external geo-IP calls are made.
export function randomClientIp() {
  const octet = () => Math.floor(Math.random() * 254) + 1;
  return `10.${octet()}.${octet()}.${octet()}`;
}

// Registers a throwaway user, logs in (JWT cookie lands in the setup VU's cookie jar)
// and creates `count` short URLs. Returns their short codes.
export function createShortUrls(count) {
  const email = `loadtest-${Date.now()}@example.com`;
  const password = 'loadtest-password';
  const json = { 'Content-Type': 'application/json' };

  http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({ email, password }),
    { headers: { ...json, 'X-Forwarded-For': randomClientIp() } });
  const login = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password }),
    { headers: { ...json, 'X-Forwarded-For': randomClientIp() } });
  check(login, { 'login ok': (r) => r.status === 200 });

  const codes = [];
  for (let i = 0; i < count; i++) {
    const res = http.post(`${BASE_URL}/api/v1/urls`,
      JSON.stringify({ longUrl: `https://example.com/load/${i}`, ttlSeconds: 86400 }),
      { headers: { ...json, 'X-Forwarded-For': randomClientIp() } });
    if (res.status === 201) {
      codes.push(res.json().shortUrl); // the API returns the short code in `shortUrl`
    }
  }
  if (codes.length === 0) {
    throw new Error('Could not create any short URLs; is the app running and reachable at ' + BASE_URL + '?');
  }
  return codes;
}

// Reads a counter from the Prometheus endpoint (sum over all label sets).
export function readCounter(name) {
  const res = http.get(`${BASE_URL}/actuator/prometheus`);
  let total = 0;
  for (const line of res.body.split('\n')) {
    if (line.startsWith(name + ' ') || line.startsWith(name + '{')) {
      total += parseFloat(line.substring(line.lastIndexOf(' ') + 1));
    }
  }
  return total;
}
