import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const longUrl = `https://example.com/page-${Math.random()}`;
  const payload = JSON.stringify({
    longUrl: longUrl,
    ttlSeconds: 3600
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  if (Math.random() < 0.1) {
    const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
    check(createRes, {
      'create status is 201': (r) => r.status === 201,
    });
  } else {
    const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
    if (createRes.status === 201) {
      const shortCode = createRes.json().shortCode;
      for (let i = 0; i < 9; i++) {
        const redirectRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
        check(redirectRes, {
          'redirect status is 302': (r) => r.status === 302,
        });
        sleep(0.1);
      }
    }
  }

  sleep(1);
}
