# Nebula Privacy Policy

Effective date: August 1, 2026

This policy explains what information Nebula processes, why it is needed, how
long it is kept, and the controls available to users. It applies to the Nebula
Android app and the account, update, mod-catalog, licensing, and compatibility
services used by the app.

## Privacy commitment

User data is processed only through Nebula's proprietary server and Nebula's
Firebase project. Nebula never sells user data. Nebula does not use user data
for advertising, profiling, data brokerage, or any purpose unrelated to
providing, securing, maintaining, and supporting Nebula.

## Information Nebula processes

### Account and authentication data

An account may contain an email address, display name, username, authentication
provider, Firebase user identifier (UUID), activation status, and activation-key
assignment. Firebase Authentication processes email/password and Google
sign-in. Nebula's proprietary server does not receive or store a user's Google
or email-account password.

### Authorized-device and session data

Nebula stores an Android-scoped device identifier, a user-readable device name,
authorization time, last-active time, and the server sessions issued to that
device. This information is needed to display and manage Authorized Devices,
keep users signed in, revoke access, and restrict NebulaCompat downloads to
confirmed accounts.

### Operational records

Nebula's proprietary server creates limited error and service records when
needed to diagnose failures, prevent abuse, secure accounts, and keep the
service working. Nebula does not collect location information for accounts and
does not use operational records for advertising or profiling.

### Information stored only on the device

The app stores its settings, profiles, installed mods, runtime components,
downloaded license texts, and game-management state in its Android application
storage. Authentication and session tokens are encrypted using a
non-exportable Android Keystore key and are excluded from Android backup and
device transfer. Local mods and game files are not uploaded as account data.

## How information is obtained

Nebula receives account details when a user creates an account, signs in, or
links an authentication method. Device and session information is created when
the user authorizes and uses a device. Operational records are created by the
server while handling requests or errors. Nebula does not obtain account data
from data brokers or sell access to user information.

## How information is used

Nebula uses information only to:

- create, authenticate, activate, and maintain accounts;
- link authentication methods after the user confirms the link;
- authorize devices and let users review or remove them;
- provide authenticated NebulaCompat downloads;
- provide app updates, the mod catalog, manifests, and license texts;
- prevent abuse and protect the service and its users;
- diagnose failures, maintain the service, and respond to user support needs;
- carry out account or device deletion requested by the user.

Ordinary mod downloads are not authentication-gated. A current confirmed
session is required to download NebulaCompat.

## Where information is processed and disclosed

User data goes only through Nebula's proprietary server and Nebula's Firebase
project. Firebase provides the account authentication and account-record
infrastructure used by Nebula. Nebula does not sell, rent, license, trade, or
otherwise disclose user data to advertisers, data brokers, or unrelated third
parties.

## Retention and deletion

- Account records are retained until the user deletes the account.
- Authorized-device records and their sessions are retained until that device
  is removed, it logs out, or the account is deleted.
- Operational error and service records are retained for no more than seven
  days.
- On-device files remain until the app removes them, the user removes them, or
  Android clears Nebula's application data.

Selecting **Delete Account** in Nebula Settings immediately deletes the
Firebase Authentication identity, Nebula profile, email index, activation
assignment, authorized-device records, and sessions tied to that account UUID.
Local mods and game files remain on the device. Removing a device from
**Authorized Devices** immediately deletes that device's sessions. Logging out
removes that device's stored authentication state and cached NebulaCompat
component.

## User choices and controls

Users can review authorized devices, remove a device and revoke its sessions,
log out, or permanently delete their account from Nebula Settings. Android's
app settings can be used to clear all Nebula data stored on a device. Account
deletion cannot be undone.

## Security

Nebula uses authenticated server sessions, confirmed-account checks for
protected downloads, Android Keystore encryption for tokens, and restricted
Android application storage. No system can guarantee absolute security, so
users should protect their device and authentication credentials and promptly
remove devices they no longer control.

## Changes to this policy

If Nebula's data practices materially change, this policy will be updated with
a new effective date. A changed policy does not permit Nebula to sell existing
user data or use it for unrelated purposes.

This policy describes Nebula version 1.2.2 and the corresponding API behavior.
