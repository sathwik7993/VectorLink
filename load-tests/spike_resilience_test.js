import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '10s', target: 800 },
    { duration: '30s', target: 800 },
    { duration: '10s', target: 10 },
    { duration: '10s', target: 10 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const longUrl = `https://example.com/spike-${Math.random()}`;
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
    
    for (let i = 0; i < 20; i++) {
      const redirectRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
      check(redirectRes, {
        'redirect status is 302': (r) => r.status === 302,
      });
      sleep(0.01);
    }
  }
  sleep(0.5);
}
