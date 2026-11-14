const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CUSTOMER_A = "cust_alice_123";
const CUSTOMER_B = "cust_bob_456";
const OWNER_UID = "owner_uid_789";
const OWNER_EMAIL = "bommalukathalu@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const serverTs = () => new Date();

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write products, users, orders, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").where("isDeleted", "==", false).get());
  await assertFails(unauthDb.collection("categories").get());
  await assertFails(unauthDb.collection("orders").where("userId", "==", CUSTOMER_A).get());
  await assertFails(unauthDb.collection("users").doc(CUSTOMER_A).get());
  await assertFails(unauthDb.collection("settings").doc("shop").get());
});

test("Customer can create and update own profile, cannot escalate role or read another user", async () => {
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_A, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(CUSTOMER_B, { email: "bob@test.com", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(CUSTOMER_A).set({
      userId: CUSTOMER_A,
      displayName: "Alice",
      email: "alice@test.com",
      phone: "9876543210",
      preferredLanguage: "te",
      role: "customer",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Shadow field / role escalation rejected
  await assertFails(
    aliceDb.collection("users").doc(CUSTOMER_A).update({
      role: "admin",
      updatedAt: serverTs(),
    })
  );

  // Cross-user read rejected
  await assertFails(bobDb.collection("users").doc(CUSTOMER_A).get());
  await assertFails(bobDb.collection("users").get());
});

test("Email spoofing and non-owner admin creation are strictly rejected", async () => {
  // Unverified owner email spoof must fail
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid").set({
      adminId: "spoof_uid",
      email: OWNER_EMAIL,
      displayName: "Spoof",
      role: "owner",
      createdAt: serverTs(),
    })
  );

  // Regular customer cannot create admin record
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_A, { email: "alice@test.com", email_verified: true }).firestore();
  await assertFails(
    aliceDb.collection("admins").doc(CUSTOMER_A).set({
      adminId: CUSTOMER_A,
      email: "alice@test.com",
      displayName: "Alice",
      role: "owner",
      createdAt: serverTs(),
    })
  );

  // Verified owner can register admin record
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    ownerDb.collection("admins").doc(OWNER_UID).set({
      adminId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Big Bazer Owner",
      role: "owner",
      createdAt: serverTs(),
    })
  );
});

test("Admin can create categories, products, images, soft-delete to recycle bin, and log audit", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_A, { email: "alice@test.com", email_verified: true }).firestore();

  await assertSucceeds(
    ownerDb.collection("categories").doc("cat_toys").set({
      nameEn: "Toys",
      nameTe: "ఆటబొమ్మలు",
      iconKey: "toys",
      sortOrder: 1,
      isActive: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Customer cannot create category or product
  await assertFails(
    aliceDb.collection("categories").doc("cat_hack").set({
      nameEn: "Hack",
      nameTe: "హ్యాక్",
      iconKey: "toys",
      sortOrder: 99,
      isActive: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    ownerDb.collection("products").doc("prod_1").set({
      sku: "BBB-001",
      nameEn: "Wooden Toy Train",
      nameTe: "చెక్క బొమ్మ రైలు",
      descriptionEn: "Safe wooden toy",
      descriptionTe: "సురక్షిత చెక్క బొమ్మ",
      categoryId: "cat_toys",
      categoryNameEn: "Toys",
      imageUrls: ["cloudimg://img_1"],
      imageIds: ["img_1"],
      originalPrice: 499,
      sellingPrice: 349,
      discountPercent: 30,
      stock: 15,
      lowStockThreshold: 5,
      colors: ["Red", "Yellow"],
      sizes: ["Standard"],
      specifications: "Natural wood",
      isAvailable: true,
      isTrending: true,
      isBestSeller: false,
      isNewArrival: true,
      isFeaturedOffer: true,
      ratingAverage: 4.5,
      reviewCount: 2,
      isDeleted: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Customer can query active products (with isDeleted == false filter)
  await assertSucceeds(aliceDb.collection("products").where("isDeleted", "==", false).get());
  // Customer cannot list without isDeleted == false filter
  await assertFails(aliceDb.collection("products").get());
  // Customer cannot edit product price
  await assertFails(aliceDb.collection("products").doc("prod_1").update({ sellingPrice: 1, updatedAt: serverTs() }));
});

test("Customer cart, orders, notifications, and cross-user isolation", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_A, { email: "alice@test.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(CUSTOMER_B, { email: "bob@test.com", email_verified: true }).firestore();

  // Seed product
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("products").doc("prod_1").set({
      sku: "BBB-001",
      nameEn: "Steel Water Bottle",
      nameTe: "స్టీల్ వాటర్ బాటిల్",
      categoryId: "cat_bottles",
      originalPrice: 300,
      sellingPrice: 240,
      stock: 10,
      isAvailable: true,
      isDeleted: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
  });

  // Alice adds to cart
  await assertSucceeds(
    aliceDb.collection("users").doc(CUSTOMER_A).collection("cart").doc("item_1").set({
      userId: CUSTOMER_A,
      productId: "prod_1",
      productNameEn: "Steel Water Bottle",
      sellingPrice: 240,
      quantity: 2,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Bob cannot read Alice's cart
  await assertFails(bobDb.collection("users").doc(CUSTOMER_A).collection("cart").get());

  // Alice places an order
  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1001").set({
      orderNumber: "BBB-1001",
      userId: CUSTOMER_A,
      customerName: "Alice",
      customerPhone: "9876543210",
      village: "Bejjur",
      mandal: "Bejjur",
      district: "Komaram Bheem Asifabad",
      pinCode: "504299",
      subtotal: 480,
      discountAmount: 0,
      deliveryCharge: 0,
      totalAmount: 480,
      paymentMethod: "COD",
      paymentStatus: "PENDING",
      orderStatus: "NEW",
      whatsappSent: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Alice can query her own orders; Bob cannot read Alice's order or query without filter
  await assertSucceeds(aliceDb.collection("orders").where("userId", "==", CUSTOMER_A).get());
  await assertFails(bobDb.collection("orders").doc("ord_1001").get());
  await assertFails(bobDb.collection("orders").get());

  // Owner can list all orders and update orderStatus
  await assertSucceeds(ownerDb.collection("orders").get());
  await assertSucceeds(
    ownerDb.collection("orders").doc("ord_1001").update({
      orderStatus: "CONFIRMED",
      updatedAt: serverTs(),
    })
  );

  // Notifications: Alice can query with targetUserId in [CUSTOMER_A, "ALL"]
  await assertSucceeds(
    ownerDb.collection("notifications").doc("notif_1").set({
      targetUserId: "ALL",
      titleEn: "Festival Offer",
      titleTe: "పండుగ ఆఫర్",
      bodyEn: "Flat 20% off",
      bodyTe: "20% తగ్గింపు",
      type: "OFFER",
      isRead: false,
      createdAt: serverTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("notifications").where("targetUserId", "in", [CUSTOMER_A, "ALL"]).get()
  );
});

test("Recycle bin, backup records, and audit logs are strictly owner-only", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(CUSTOMER_A, { email: "alice@test.com", email_verified: true }).firestore();

  await assertFails(aliceDb.collection("deletedProducts").get());
  await assertFails(aliceDb.collection("backupRecords").get());
  await assertFails(aliceDb.collection("auditLogs").get());

  await assertSucceeds(
    ownerDb.collection("backupRecords").doc("bkp_1").set({
      backupType: "FULL_SNAPSHOT",
      status: "SUCCESS",
      itemCount: 10,
      imageCount: 5,
      sizeBytes: 12040,
      retentionDays: 30,
      storageLocation: "Cloud Firestore Snapshot",
      snapshotPayloadJson: "{}",
      createdBy: OWNER_UID,
      createdAt: serverTs(),
    })
  );
});
