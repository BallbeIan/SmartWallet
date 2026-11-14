# SmartWallet privacy policy

*Effective 14 November 2026*

SmartWallet is a personal expense tracker for Android. This page explains what the app does with your information. The short version: **it collects nothing, and it never connects to the internet.**

## What SmartWallet stores

Everything you enter (accounts, transactions, categories, budgets, scheduled payments and your settings) is stored **only on your phone**, in the app's private storage. The developer cannot see it, and it is never sent anywhere by the app.

## No internet access

SmartWallet does not request the Android internet permission, so it technically cannot send data over the network. It has no accounts, no sign-in, no advertising, no analytics, no crash reporting and no third-party tracking.

## Permissions

| Permission | Why | Required? |
|---|---|---|
| Notifications | To remind you before a scheduled payment is due. Asked for only when you switch reminders on. | No |
| Biometrics | To unlock the app with your fingerprint or face if you switch that on. The check is done by Android; SmartWallet never receives or stores your fingerprint or face data. | No |
| Run at startup, keep awake, foreground service, network state | Declared by the Android WorkManager library so the daily check for due payments and reminders keeps working after a restart. SmartWallet does not use the network. | Automatic |

## App lock

If you set a PIN, only a salted, one-way hash of it is stored on your phone, never the PIN itself. If you forget it, the app can only be reopened with your phone's own screen lock (on Android 11 and newer) or by clearing the app's data, which deletes everything in it.

## Exporting and importing

Exporting a CSV file saves a copy of your data to a place you choose through Android's file picker. From then on that file is yours to manage. Importing reads a file you pick. SmartWallet does not upload either.

## Backups

Android may include an app's data in the device backup you control in your phone's settings (for example a Google backup). That is handled by your phone and your Google account under their own terms, not by SmartWallet. You can turn it off in your phone's settings.

## Deleting your data

Uninstalling SmartWallet, or choosing *Clear storage* in the app's system settings, removes all of its data from your phone.

## Children

SmartWallet is a general-purpose tool and is not aimed at children. It collects no personal information from anyone.

## Changes

If this policy changes, the new version will be published at this address with a new effective date.

## Contact

Questions or concerns: open an issue at <https://github.com/BallbeIan/SmartWallet/issues>.
