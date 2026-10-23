const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: "127.0.0.1",
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read profiles or create checkin", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("profiles").get());
  await assertFails(unauthDb.collection("checkins").doc("run1_user1").set({
    runId: "run1",
    userId: "user1",
    lat: 37.7749,
    lng: -122.4194,
    accuracyM: 10,
    createdAt: new Date(),
  }));
});

test("Authenticated user: can create own profile and read profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("profiles").doc(ALICE_UID).set({
    userId: ALICE_UID,
    name: "Alice Runner",
    city: "San Francisco",
    createdAt: new Date(),
  }));

  // Bob can read Alice's profile
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("profiles").doc(ALICE_UID).get());
});

test("Authenticated user: cannot forge another user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("profiles").doc(BOB_UID).set({
    userId: BOB_UID,
    name: "Bob Impersonator",
    city: "San Francisco",
    createdAt: new Date(),
  }));
});

test("Check-in validation: fails if accuracyM > 50", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("checkins").doc(`run1_${ALICE_UID}`).set({
    runId: "run1",
    userId: ALICE_UID,
    userName: "Alice",
    lat: 37.7749,
    lng: -122.4194,
    accuracyM: 65.0, // > 50m rejected!
    createdAt: new Date(),
  }));
});

test("Check-in validation: succeeds if accuracyM <= 50 and id matches pattern", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("checkins").doc(`run1_${ALICE_UID}`).set({
    runId: "run1",
    userId: ALICE_UID,
    userName: "Alice",
    lat: 37.7749,
    lng: -122.4194,
    accuracyM: 20.0,
    distanceToMeetingM: 45.0,
    createdAt: new Date(),
  }));
});
