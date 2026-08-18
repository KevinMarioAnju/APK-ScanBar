# Login System and Role-Based Access Control

The goal is to provide a clear set of credentials for Admin and Inspector roles and implement role-based access control (RBAC) in the application. Specifically, the "Direktori Pekerja" button must be hidden for the Inspector role.

## User Review Required

> [!IMPORTANT]
> Standardized credentials:
> - **Admin**: Username: `admin`, Password: `admin123`
> - **Inspektur**: Username: `inspektur`, Password: `inspektur123`
>
> Access Control:
> - **Admin**: Can see both "Scan Barcode" and "Direktori Pekerja".
> - **Inspektur**: Can ONLY see "Scan Barcode". (The "Direktori Pekerja" button will be set to `View.GONE`)

## Proposed Changes

### Authentication Component

#### [InspectorLoginActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/InspectorLoginActivity.java)

- Standardize credentials to `admin` / `admin123` and `inspektur` / `inspektur123`.
- Pass the logged-in role to `MainActivity` via an `Intent` extra named `"ROLE"`.

```java
Intent intent = new Intent(this, MainActivity.class);
intent.putExtra("ROLE", role); // "admin" or "inspektur"
startActivity(intent);
```

#### [LoginActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/LoginActivity.java)

- Synchronize credentials and role passing with `InspectorLoginActivity`.

---

### Main Application Logic

#### [MainActivity.java](file:///D:/ScanBar/app/src/main/java/com/example/scanbar/MainActivity.java)

- Retrieve the `"ROLE"` extra from the `Intent` in `onCreate`.
- If the role is `"inspektur"`, set the visibility of `binding.btnNavDirectory` to `View.GONE`.

```java
import android.view.View;
// ...
String role = getIntent().getStringExtra("ROLE");
if ("inspektur".equals(role)) {
    binding.btnNavDirectory.setVisibility(View.GONE);
}
```

## Verification Plan

### Manual Verification
1. **Admin Login**:
   - Log in with `admin` / `admin123`.
   - Verify that "Direktori Pekerja" is visible and clickable.
2. **Inspector Login**:
   - Log in with `inspektur` / `inspektur123`.
   - Verify that "Direktori Pekerja" is **NOT** visible.
3. **Failed Login**:
   - Verify that incorrect credentials show an error toast.
