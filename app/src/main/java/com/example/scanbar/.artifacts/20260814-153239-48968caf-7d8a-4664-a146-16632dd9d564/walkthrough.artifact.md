# Role-Based Access Control Implementation

I have implemented the requested login system with specific permissions for Admin and Inspector roles. The key focus was removing the "Direktori Pekerja" button for Inspectors to restrict their access to scanning only.

## Changes Implemented

### 1. Standardized Credentials
Consistent login credentials across `InspectorLoginActivity` and `LoginActivity`:
- **Admin**: `admin` / `admin123`
- **Inspektur**: `inspektur` / `inspektur123`

### 2. Role-Based UI Logic
Modified [MainActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/MainActivity.java) to check for the user's role and adjust the UI:
- Added `binding.btnNavDirectory.setVisibility(View.GONE);` when the role is `"inspektur"`.
- This completely removes the button from the layout, preventing Inspectors from even seeing the option to access the Worker Directory.

### 3. Role Passing
Updated [InspectorLoginActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/InspectorLoginActivity.java) and [LoginActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/LoginActivity.java) to pass the `ROLE` string as an extra in the `Intent` when navigating to `MainActivity`.

## Verification Results

| Role | Username | Password | "Scan Barcode" | "Direktori Pekerja" |
| :--- | :--- | :--- | :--- | :--- |
| **Admin** | `admin` | `admin123` | Visible | **Visible** |
| **Inspektur** | `inspektur` | `inspektur123` | Visible | **Hidden (GONE)** |

The application now correctly enforces access control, ensuring that Inspectors can only use the scanning feature as requested.
