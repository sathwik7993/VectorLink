import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 200 },
    { duration: '30s', target: 200 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 1000 },
    { duration: '1m', target: 1000 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const longUrl = `https://example.com/stress-${Math.random()}`;
  const payload = JSON.stringify({
    longUrl: longUrl,
    ttlSeconds: 3600
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
  if (createRes.status === 201) {
    const shortCode = createRes.json().shortCode;
    
    for (let i = 0; i < 5; i++) {
      const redirectRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
      check(redirectRes, {
        'redirect status is 302': (r) => r.status === 302,
      });
      sleep(0.05);
    }
  }
  sleep(0.5);
}
