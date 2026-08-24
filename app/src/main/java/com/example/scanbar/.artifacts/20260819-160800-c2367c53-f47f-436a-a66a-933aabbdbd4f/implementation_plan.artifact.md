# Implementation Plan - Admin Role and Account Management

Add an Admin role that can manage (create and delete) accounts for Inspectors, using a database-backed authentication system instead of hardcoded credentials.

## Proposed Changes

### Data Layer

#### [User.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/data/User.java) [NEW]
- Create a new Room entity `User` with fields: `id` (PK), `username`, `password`, and `role`.

#### [UserDao.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/data/UserDao.java) [NEW]
- Create `UserDao` with methods for login, getting all inspectors, inserting, and deleting users.

#### [AppDatabase.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/data/AppDatabase.java)
- Add `User` entity to the `@Database` annotation.
- Increase database version to `9`.
- Add `public abstract UserDao userDao();`.
- Update `onOpen` to pre-populate default `admin` and `inspektur` accounts if the user table is empty.

---

### Authentication

#### [InspectorLoginActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/InspectorLoginActivity.java)
- Replace hardcoded credential checks with a call to `userDao().login(username, password)`.
- Use the `role` returned from the database to navigate to `MainActivity`.

---

### UI - Main Activity

#### [activity_main.xml](file:///D:/ScanBar/app/src/main/res/layout/activity_main.xml)
- Update `bottomNavCard` to include a third button `btnNavAccount` with the same style as `Scan` and `Directory`.
- Ensure the layout is responsive and aesthetically consistent with the teal accent theme.

#### [MainActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/MainActivity.java)
- Update `onCreate` to handle the `btnNavAccount` click.
- For Admin, show all three buttons. For Inspector, hide the navigation bar as per existing logic (or as per role restriction).
- Update `updateNavUI` to manage the active state for three buttons (Scan, Directory, Account).
- Implement `loadFragment(new AccountFragment())` when the Account button is clicked.

---

### UI - Account Management

#### [fragment_manage_accounts.xml](file:///D:/ScanBar/app/src/main/res/layout/fragment_manage_accounts.xml) [NEW]
- Layout with a `RecyclerView` for the user list and an `ExtendedFloatingActionButton` to add a new account.

#### [item_user.xml](file:///D:/ScanBar/app/src/main/res/layout/item_user.xml) [NEW]
- Layout for the user list item, showing username and role, with a delete button.

#### [dialog_add_user.xml](file:///D:/ScanBar/app/src/main/res/layout/dialog_add_user.xml) [NEW]
- Layout for the dialog to create a new Inspector account.

#### [UserAdapter.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/UserAdapter.java) [NEW]
- RecyclerView adapter for the `User` entity.

#### [ManageAccountsFragment.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/ManageAccountsFragment.java) [NEW]
- Fragment implementation to display and manage Inspector accounts.

## Verification Plan

### Manual Verification
1.  **Database Migration**: Run the app and verify that the database version upgrades and pre-populates the default accounts.
2.  **Login**:
    *   Login as `admin` / `admin123`. Verify access to all tabs including "Accounts".
    *   Login as `inspektur` / `inspektur123`. Verify restricted access (bottom nav hidden or "Accounts" hidden).
3.  **Account Management**:
    *   As Admin, go to "Accounts" tab.
    *   Create a new Inspector account (e.g., `test_inspector` / `pass123`).
    *   Verify the new account appears in the list.
    *   Logout and try logging in with the new account.
    *   As Admin, delete an account and verify it can no longer log in.
