import { defineRailway, postgres, preserve, project, service } from "railway/iac";

export default defineRailway(() => {
  const Postgres = postgres("Postgres", { region: "us-west2" });

  const bumpApi = service("bump-api", {
    // The repo root is the Android app; only backend/ is the service.
    rootDirectory: "backend",
    healthcheck: "/health",
    replicas: { "us-west2": 1 },
    env: { DATABASE_URL: preserve() },
  });

  return project("bump-sync", {
    resources: [bumpApi, Postgres],
  });
});
