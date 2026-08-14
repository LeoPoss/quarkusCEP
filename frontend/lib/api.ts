import ky from "ky";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const api = ky.create({ prefix: API_BASE_URL });
