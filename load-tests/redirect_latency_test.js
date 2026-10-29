// Measures redirect latency (cache-hit path) and fails if P99 is 5 ms or more.
//   k6 run load-tests/redirect_latency_test.js
//   k6 run -e BASE_URL=http://host:8080 -e RATE=300 load-tests/redirect_latency_test.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, createShortUrls, randomClientIp } from './lib/setup.js';

const RATE = parseInt(__ENV.RATE || '200');

export const options = {
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    // JIT warm-up and cache fill; excluded from the thresholds
    warmup: {
      executor: 'constant-arrival-rate',
      rate: RATE, timeUnit: '1s', duration: '30s',
      preAllocatedVUs: 50, maxVUs: 200,
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-arrival-rate',
      startTime: '30s',
      rate: RATE, timeUnit: '1s', duration: '2m',
      preAllocatedVUs: 50, maxVUs: 200,
      tags: { phase: 'measure' },
    },
  },
  thresholds: {
    'http_req_duration{phase:measure}': ['p(99)<5'],
    'http_req_failed{phase:measure}': ['rate<0.001'],
  },
};

export function setup() {
  return { codes: createShortUrls(50) };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    headers: { 'X-Forwarded-For': randomClientIp(), 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) Chrome/120.0' },
  });
  check(res, { 'redirect is 302': (r) => r.status === 302 });
}
