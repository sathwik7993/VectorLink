import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    longUrl: 'https://github.com/grafana/k6',
    ttlSeconds: 3600
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. Create short URL
  const createRes = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
  check(createRes, {
    'create status is 201': (r) => r.status === 201,
    'has shortCode': (r) => r.json().shortCode !== undefined,
  });

  if (createRes.status === 201) {
    const shortCode = createRes.json().shortCode;

    // 2. Redirect to long URL
    const redirectRes = http.get(`${BASE_URL}/${shortCode}`, { redirects: 0 });
    check(redirectRes, {
      'redirect status is 302': (r) => r.status === 302,
      'has correct Location header': (r) => r.headers['Location'] === 'https://github.com/grafana/k6',
    });
  }

  sleep(1);
}
