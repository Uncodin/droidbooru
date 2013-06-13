DroidBooru
==========

An Android client for nodebooru

Building
----------

DroidBooru uses the same Nativ model file as nodebooru, which is why it's
registered as submodule. In order to stay in sync with nodebooru's bindings,
the DroidBooru build script uses nodebooru's submodule directly.

Ensure that the Android SDK's `tools` directory is in your `PATH`, and run
the `process_model` script to build the Android bindings.
