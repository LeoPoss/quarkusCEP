# syntax=docker.io/docker/dockerfile:1

FROM oven/bun:1 AS base

# Install dependencies and build the application
FROM base AS builder
WORKDIR /app

# Copy package files first for better caching
COPY bun.lock package.json ./

# Install dependencies with bun
RUN bun install --frozen-lockfile

# Copy the rest of the application
COPY . .

# Build the application
RUN bun run build --no-lint

# Production image
FROM base AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Create a non-root user
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

# Copy built files from builder
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

# Switch to non-root user
USER nextjs

EXPOSE 3000

# Start the application
CMD ["bun", "run", "server.js"]