# Contributing

Thanks for your interest in SmartWallet! Bug reports, device reports and pull requests are welcome.

## Reports

SmartWallet has been developed and tested on one Samsung foldable. If you try it on another phone, please open a **Bug report** issue, even when everything works, and include your phone model and Android version.

## Building

See [Building from source](README.md#building-from-source). Run `./gradlew lint assembleDebug` before opening a pull request.

## Pull requests

- Keep changes focused, and describe what you tested and on which device.
- Match the existing style: small composables, state in `WalletViewModel`, and no new third-party libraries without a good reason.
- Money is always whole cents; never store or add up floating-point amounts.
- The app must not request the internet permission.

## Licensing of contributions

The project is under the [PolyForm Noncommercial License 1.0.0](LICENSE). By submitting a contribution you confirm that you wrote it (or have the right to submit it) and that it may be distributed under that license.

## Reporting security problems

Please do not open a public issue for a security problem. Use GitHub's private vulnerability reporting for this repository if it is enabled, or contact the maintainer through their GitHub profile.
