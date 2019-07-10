import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_midi/model/midi_event.dart';

import 'local_storage.dart';

class FlutterMidi {
  static const MethodChannel _channel = MethodChannel('flutter_midi');

  /// Needed so that the sound font is loaded
  /// On iOS make sure to include the sound_font.SF2 in the Runner folder.
  /// This does not work in the simulator.
  static Future<bool> prepare({@required ByteData sf2, String name = "instrument.sf2"}) async {
    File _file = await writeToFile(sf2, name: name);

    final Map<dynamic, dynamic> mapData = <dynamic, dynamic>{};
    mapData["path"] = _file.path;
    print("Path => ${_file.path}");
    return _channel.invokeMethod('prepare_midi', mapData);
  }

  static Future prepareMidiFile(Uint8List midiBytes) {
    return _channel.invokeMethod('prepare_midi_file', {'midi_file': midiBytes});
  }

  ///Resumes midi files and returns the synsesizer delay in microseconds
  static Future<int> resumeFile({bool paused = false}) async {
    var delay = await _channel.invokeMethod('resume_midi_file', {'paused' : paused});
    return delay as int;
  }

  /// Needed to play midi notes from file presented as [Uint8List] and returns synth delay in microseconds
  static Future<int> playFile({@required Uint8List midiData}) async {
    var delay = await _channel.invokeMethod("play_midi_file", {"midi_file": midiData});
    return delay as int;
  }

  /// Needed to stop playind midi notes from file and reset midi processor
  static Future<void> stopPlayingFile() async {
    await _channel.invokeMethod("stop_playing");
    await _channel.invokeMethod("reset_processor");
  }

  /// Needed to pause playing midi notes from file
  static Future<void> pausePlayingFile() async {
    await _channel.invokeMethod("stop_playing");
  }

  /// Creates midi event stream
  static Stream<MidiEvent> onMidiEvent() {
    EventChannel _eventChannel = EventChannel('midi_event_channel');

    return _eventChannel
        .receiveBroadcastStream()
        .map((midiEventJson) => MidiEvent.fromJson(midiEventJson));
  }

  /// Fetches all notes from midi file
  static Future<List<int>> getNotesFormFile(Uint8List midiFile) async {
    var notesList = await _channel.invokeMethod("get_notes", {'midi_file': midiFile});
    return List<int>.from(notesList);
  }

  /// Needed so that the sound font is loaded
  /// On iOS make sure to include the sound_font.SF2 in the Runner folder.
  /// This does not work in the simulator.
  static Future<String> changeSound(
      {@required ByteData sf2, String name = "instrument.sf2"}) async {
    File _file = await writeToFile(sf2, name: name);

    final Map<dynamic, dynamic> mapData = <dynamic, dynamic>{};
    mapData["path"] = _file.path;
    print("Path => ${_file.path}");
    final String result = await _channel.invokeMethod('change_sound', mapData);
    print("Result: $result");
    return result;
  }

  /// Unmute the device temporarly even if the mute switch is on or toggled in settings.
  static Future<String> unmute() async {
    final String result = await _channel.invokeMethod('unmute');
    return result;
  }

  static Future<void> changeVolumeLevel(int volumeLevel) {
    _channel.invokeMethod('change_volume_level', {'volume_level': volumeLevel});
  }

  static Future<int> getNoteValue(String note) {
    return _channel.invokeMethod('get_note_value', {'note_string': note});
  }

  /// Use this when stopping the sound onTouchUp or to cancel a long file.
  /// Not needed if playing midi onTap.
  static Future<String> stopMidiNote({
    @required int midi,
  }) async {
    final Map<dynamic, dynamic> mapData = <dynamic, dynamic>{};
    print("Pressed: $midi");
    mapData["note"] = midi;
    final String result = await _channel.invokeMethod('stop_midi_note', mapData);
    return result;
  }

  /// Play a midi note from the sound_font.SF2 library bundled with the application.
  /// Play a midi note in the range between 0-256
  /// Multiple notes can be played at once as seperate calls.
  static Future<String> playMidiNote({
    @required int midi,
  }) async {
    final Map<dynamic, dynamic> mapData = <dynamic, dynamic>{};
    print("Pressed: $midi");
    mapData["note"] = midi;
    return await _channel.invokeMethod('play_midi_note', mapData);
  }
}
