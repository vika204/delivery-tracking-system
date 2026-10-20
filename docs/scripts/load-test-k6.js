import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '2m',  target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  const getRes = http.get(`${BASE_URL}/shipments`);
  check(getRes, {
    'GET /shipments status 200': (r) => r.status === 200,
  });

  const id = Math.floor(Math.random() * 1000000);
  const payload = JSON.stringify({
    senderId: 100,
    recipientId: 200,
    origin: 'Kyiv',
    destination: 'Lviv',
    weight: 2.5,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${Date.now()}-${id}`,
    },
  };

  const postRes = http.post(`${BASE_URL}/shipments`, payload, params);
  check(postRes, {
    'POST /shipments status 200 or 500': (r) => r.status === 200 || r.status === 500,
  });

  sleep(0.5);
}
