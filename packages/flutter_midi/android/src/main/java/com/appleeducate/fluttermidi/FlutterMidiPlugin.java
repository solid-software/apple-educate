package com.appleeducate.fluttermidi;

import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOff;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.meta.EndOfTrack;
import com.pdrogfer.mididroid.util.MidiEventListener;
import com.pdrogfer.mididroid.util.MidiProcessor;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;
import cn.sherlock.com.sun.media.sound.SoftSynthesizer;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import jp.kshoji.javax.sound.midi.InvalidMidiDataException;
import jp.kshoji.javax.sound.midi.MidiMessage;
import jp.kshoji.javax.sound.midi.MidiUnavailableException;
import jp.kshoji.javax.sound.midi.Receiver;
import jp.kshoji.javax.sound.midi.ShortMessage;
import jp.kshoji.javax.sound.midi.MidiSystem;

import org.jfugue.MusicStringParser;
import org.jfugue.Note;
import org.jfugue.ParserListenerAdapter;
import org.jfugue.Pattern;


/**
 * FlutterMidiPlugin
 */

public class FlutterMidiPlugin implements MethodCallHandler, MidiEventListener, EventChannel.StreamHandler {
    private SoftSynthesizer synth;
    private Receiver recv;
    private int lastPlayedNote = 0;
    private MidiProcessor midiProcessor;
    private Result methodResult;
    private MidiEventListener midiEventListener;
    private double gain = 1.0;


    public FlutterMidiPlugin() {
        System.out.println("new instance of class");
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_midi");
        FlutterMidiPlugin midiPlugin = new FlutterMidiPlugin();
        channel.setMethodCallHandler(midiPlugin);

        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "midi_event_channel");
        eventChannel.setStreamHandler(midiPlugin);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("prepare_midi")) {
            try {
                String _path = call.argument("path");
                File _file = new File(_path);
                SF2Soundbank sf = new SF2Soundbank(_file);
                synth = new SoftSynthesizer();
                synth.open();
                synth.loadAllInstruments(sf);
                synth.getChannels()[0].programChange(0);
                synth.getChannels()[1].programChange(1);
                recv = synth.getReceiver();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MidiUnavailableException e) {
                e.printStackTrace();
            }
        } else if (call.method.equals("change_sound")) {
            try {
                String _path = call.argument("path");
                File _file = new File(_path);
                SF2Soundbank sf = new SF2Soundbank(_file);
                synth = new SoftSynthesizer();
                synth.open();
                synth.loadAllInstruments(sf);
                synth.getChannels()[0].programChange(0);
                synth.getChannels()[1].programChange(1);
                recv = synth.getReceiver();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MidiUnavailableException e) {
                e.printStackTrace();
            }
        } else if (call.method.equals("play_midi_note")) {
            int _note = call.argument("note");
            try {
                ShortMessage msg = new ShortMessage();
                msg.setMessage(ShortMessage.NOTE_ON, 0, _note, 127);
                recv.send(msg, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        } else if (call.method.equals("stop_midi_note")) {
            int _note = call.argument("note");
            try {
                ShortMessage msg = new ShortMessage();
                msg.setMessage(ShortMessage.NOTE_OFF, 0, _note, 127);
                recv.send(msg, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        } else if (call.method.equals("play_midi_file")) {
            byte[] midiData = call.argument("midi_file");
            try {
                InputStream midiInputStream = new ByteArrayInputStream(midiData);
                MidiFile midiFile = new MidiFile(midiInputStream);
                if (midiProcessor == null) {

                    midiProcessor = new MidiProcessor(midiFile);
                }
                midiProcessor.registerEventListener(this, NoteOn.class);
                midiProcessor.registerEventListener(this, EndOfTrack.class);
                midiProcessor.registerEventListener(this, NoteOff.class);
                midiProcessor.start();
                result.success(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (call.method.equals("on_file_end")) {
            methodResult = result;
        } else if (call.method.equals("stop_playing")) {
            if (midiProcessor != null && midiProcessor.isRunning()) {
                ShortMessage stopMessage = new ShortMessage();
                try {
                    stopMessage.setMessage(ShortMessage.NOTE_OFF, 0, lastPlayedNote, 127);
                    recv.send(stopMessage, -1);
                } catch (InvalidMidiDataException e) {
                    e.printStackTrace();
                }

                midiProcessor.unregisterAllEventListeners();
                midiProcessor.stop();
            }
            result.success(true);
        } else if (call.method.equals("reset_processor")) {
            if (midiProcessor != null) {
                midiProcessor.reset();
                midiProcessor = null;
            }
            result.success(null);
        } else if (call.method.equals("get_notes")) {
            byte[] midiData = call.argument("midi_file");
            try {
                InputStream midiFileInputStream = new ByteArrayInputStream(midiData);
                MidiFile midiFile = new MidiFile(midiFileInputStream);
                List<Integer> midiNotes = new ArrayList<>();
                int midiTracksCount = midiFile.getTrackCount();
                if (midiTracksCount > 0) {
                    Object[] midiEvents = midiFile.getTracks().get(midiTracksCount - 1).getEvents().toArray();
                    for (Object event : midiEvents){
                        if (event instanceof NoteOn){
                            midiNotes.add(((NoteOn) event).getNoteValue());
                        }
                    }
                }
                result.success(midiNotes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if (call.method.equals("get_note_value")){
            String noteString  = call.argument("note_string");
            NoteEventListener parserListener = new NoteEventListener(result);
            MusicStringParser parser = new MusicStringParser();
            parser.addParserListener(parserListener);
            Pattern pattern = new Pattern(noteString);
            parser.parse(pattern);
        }else if (call.method.equals("change_volume_level")){
            int volumeLevel = call.argument("volume_level");
            synth.getChannels()[0].controlChange(7, volumeLevel);
            result.success(true);
        }
    }

    @Override
    public void onStart(boolean b) {

    }

    @Override
    public void onEvent(MidiEvent midiEvent, long l) {
        if (midiEvent instanceof NoteOn) {
            NoteOn note = (NoteOn) midiEvent;
            lastPlayedNote = note.getNoteValue();
            ShortMessage startMessage = new ShortMessage();
            try {
                startMessage.setMessage(ShortMessage.NOTE_ON, 0, note.getNoteValue(), note.getVelocity());
                recv.send(startMessage, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        } else if (midiEvent instanceof NoteOff) {
            NoteOff note = (NoteOff) midiEvent;
            ShortMessage stopMessage = new ShortMessage();
            try {
                stopMessage.setMessage(ShortMessage.NOTE_OFF, 0, note.getNoteValue(), note.getVelocity());
                recv.send(stopMessage, -1);
            } catch (InvalidMidiDataException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStop(boolean b) {
        try {
            ShortMessage shortMessage = new ShortMessage();
            shortMessage.setMessage(ShortMessage.STOP);
            recv.send(shortMessage, -1);
            if (methodResult != null) {
                methodResult.success(true);
                methodResult = null;
            }
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        final EventChannel.EventSink midiEventSink = eventSink;
        midiEventListener = new MidiEventListener() {
            @Override
            public void onStart(boolean b) {

            }

            @Override
            public void onEvent(MidiEvent midiEvent, long l) {
                if (midiEvent instanceof NoteOn) {
                    Map<String, Integer> resultMap = new HashMap<>();
                    resultMap.put("noteState", 0);
                    resultMap.put("midiNote", ((NoteOn) midiEvent).getNoteValue());
                    midiEventSink.success(resultMap);
                } else if (midiEvent instanceof NoteOff) {
                    Map<String, Integer> resultMap = new HashMap<>();
                    resultMap.put("noteState", 1);
                    resultMap.put("midiNote", ((NoteOff) midiEvent).getNoteValue());
                    midiEventSink.success(resultMap);
                }
            }

            @Override
            public void onStop(boolean b) {

            }
        };
        System.out.println(midiProcessor);
        midiProcessor.registerEventListener(midiEventListener, MidiEvent.class);
    }

    @Override
    public void onCancel(Object o) {
        if (midiEventListener != null && midiProcessor != null) {
            midiProcessor.unregisterEventListener(midiEventListener);
        }
    }
}
