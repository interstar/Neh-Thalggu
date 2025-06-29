(ns goldenpond.dsl
  (:require [instaparse.core :as insta]
            [clostache.parser :refer [render]]
            [clojure.string :as str]
            [clojure.data.json :as json]))

(def grammar
  "Music = Global <NL> Chords <NL> Line (<NL> Line)*
  <Global> = Root <SPACE> Scale <SPACE> BPM <SPACE>?
  Chords = #\"[^\r\n]*\"
  Line = Chan <SPACE> Velocity <SPACE> Pattern
  Root = #\"[0-9]+\"
  Scale = \"Major\" | \"Minor\" | \"MelodicMinor\" | \"HarmonicMinor\"
  BPM = #\"[0-9]+\"
  Chan = #\"(0?[0-9]|1[0-6])\"
  Velocity = #\"[0-9]+\"
  <Pattern> = Simple | Euclidean | Explicit
  Simple = #\"[0-9]+/[0-9]+\" <SPACE> Type <SPACE> Density
  Euclidean = #\"[0-9]+%[0-9]+\" <SPACE> Type <SPACE> Density
  Explicit = #\"[0-9c><=tr.]+\" <SPACE> Density
  Type = #\"[0-9c><=tr]\"
  Density = #\"[0-9]+\"
  NL = #'\r?\n'
  SPACE = #\"\\s+\"
  ")

(def parser (insta/parser grammar))

(defn parse-grammar-rules [grammar-str]
  "Parse the grammar string and extract rule definitions as a map."
  (let [lines (str/split-lines grammar-str)
        rule-pattern #"^(\w+)\s*=\s*(.+)$"
        rules (->> lines
                   (map #(re-find rule-pattern %))
                   (filter some?)
                   (map (fn [[_ rule-name rule-body]]
                          [rule-name (str/trim rule-body)]))
                   (into {}))]
    rules))

(defn parse-goldenpond-input [input tag-path-fn]
  (try
    (let [;; Clean the input: strip trailing whitespace from each line and remove trailing newlines
          cleaned-input (-> input
                           (str/trim)  ; Remove leading/trailing whitespace
                           (str/replace #"\n\s*\n$" "\n")  ; Remove trailing empty lines
                           (str/replace #"\n$" ""))  ; Remove trailing newline
          parse-result (parser cleaned-input)]
      (if (insta/failure? parse-result)
        (let [failure (insta/get-failure parse-result)]
          {:success false
           :error (str "Parse error at line " (:line failure) ", column " (:column failure) 
                      ": Expected " (:reason failure) 
                      ", but found '" (:text failure) "'")})
        {:success true
         :parsed parse-result}))
    (catch Exception e
      {:success false
       :error (.getMessage e)})))

(defn extract-global-settings [parsed tag-path-fn]
  (let [music-children (rest parsed)
        root-node (first (filter #(= :Root (first %)) music-children))
        scale-node (first (filter #(= :Scale (first %)) music-children))
        bpm-node (first (filter #(= :BPM (first %)) music-children))
        root (second root-node)
        scale (second scale-node)
        bpm (second bpm-node)]
    {:root (Integer/parseInt root)
     :scale (case scale
              "Major" 0
              "Minor" 1
              "HarmonicMinor" 2
              "MelodicMinor" 3)
     :bpm (Integer/parseInt bpm)}))

(defn extract-chord-sequence [parsed tag-path-fn]
  (let [music-children (rest parsed)
        chords-node (first (filter #(= :Chords (first %)) music-children))
        chord-string (second chords-node)]
    chord-string))

(defn extract-lines [parsed tag-path-fn]
  (let [music-children (rest parsed)  ; Get all children of [:Music ...]
        line-nodes (filter #(= :Line (first %)) music-children)
        lines (map (fn [line-node]
                     (let [line-children (rest line-node)
                           chan-node (first (filter #(= :Chan (first %)) line-children))
                           velocity-node (first (filter #(= :Velocity (first %)) line-children))
                           pattern-node (first (filter #(contains? #{:Simple :Euclidean :Explicit} (first %)) line-children))
                           chan (second chan-node)
                           velocity (second velocity-node)
                           pattern (case (first pattern-node)
                                     :Simple (let [simple-children (rest pattern-node)
                                                   pattern-str (first simple-children)
                                                   type-node (first (filter #(= :Type (first %)) simple-children))
                                                   density-node (first (filter #(= :Density (first %)) simple-children))]
                                               (str pattern-str " " (second type-node) " " (second density-node)))
                                     :Euclidean (let [euclidean-children (rest pattern-node)
                                                      pattern-str (first euclidean-children)
                                                      type-node (first (filter #(= :Type (first %)) euclidean-children))
                                                      density-node (first (filter #(= :Density (first %)) euclidean-children))]
                                                  (str pattern-str " " (second type-node) " " (second density-node)))
                                     :Explicit (let [explicit-children (rest pattern-node)
                                                     pattern-str (first explicit-children)
                                                     density-node (first (filter #(= :Density (first %)) explicit-children))]
                                                 (str pattern-str " " (second density-node))))]
                       {:channel (Integer/parseInt chan)
                        :velocity (Integer/parseInt velocity)
                        :pattern pattern}))
                   line-nodes)]
    lines))

(defn compile-to-summary [input tag-path-fn load-java-class]
  (try
    (let [parse-result (parse-goldenpond-input input tag-path-fn)]
      (if (not (:success parse-result))
        {:success false
         :code [""]
         :notes (str input " is not a valid goldenpond composition")
         :warning "See :error"
         :error (:error parse-result)}
        (let [parsed (:parsed parse-result)
              global (extract-global-settings parsed tag-path-fn)
              chord-sequence (extract-chord-sequence parsed tag-path-fn)
              lines (extract-lines parsed tag-path-fn)
              
              ;; Load the Java GoldenData class
              GoldenData (load-java-class "haxe.root.GoldenData")
              golden-data (.newInstance GoldenData)
              
              ;; Configure the GoldenData object
              _ (set! (.root golden-data) (:root global))
              _ (set! (.mode golden-data) (:scale global))
              _ (set! (.chordSequence golden-data) chord-sequence)
              _ (set! (.stutter golden-data) 0)
              _ (set! (.bpm golden-data) (:bpm global))
              _ (set! (.chordDuration golden-data) 4)
              
              ;; Add lines
              _ (doseq [line lines]
                  (let [MidiInstrumentContext (load-java-class "haxe.root.MidiInstrumentContext")
                        constructor (.getConstructor MidiInstrumentContext 
                                                    (into-array Class [Integer/TYPE Integer/TYPE Double/TYPE Integer/TYPE]))
                        channel-val (int (:channel line))
                        velocity-val (int (:velocity line))
                        args (make-array Object 4)]
                    (aset args 0 (Integer/valueOf channel-val))
                    (aset args 1 (Integer/valueOf velocity-val))
                    (aset args 2 (Double/valueOf 0.8))
                    (aset args 3 (Integer/valueOf 0))
                    (let [midi-context (.newInstance constructor args)]
                      (.addLine golden-data (:pattern line) midi-context))))
              
              ;; Get the summary by calling toString()
              summary (.toString golden-data)]
          {:success true
           :code [summary]
           :notes "Generated GoldenData summary using GoldenPond library"
           :warning ""})))
    (catch Exception e
      (println "Exception in compile-to-summary:" (.getMessage e))
      (.printStackTrace e)
      {:success false
       :code [""]
       :notes "Error during compilation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn compile-to-json [input tag-path-fn load-java-class]
  (try
    (let [parse-result (parse-goldenpond-input input tag-path-fn)]
      (if (not (:success parse-result))
        {:success false
         :code [""]
         :notes (str input " is not a valid goldenpond composition")
         :warning "See :error"
         :error (:error parse-result)}
        (let [parsed (:parsed parse-result)
              global (extract-global-settings parsed tag-path-fn)
              chord-sequence (extract-chord-sequence parsed tag-path-fn)
              lines (extract-lines parsed tag-path-fn)
              
              ;; Load the Java classes
              GoldenData (load-java-class "haxe.root.GoldenData")
              ILineGenerator (load-java-class "haxe.root.ILineGenerator")
              INote (load-java-class "haxe.root.INote")
              Array (load-java-class "haxe.root.Array")
              
              ;; Create and configure GoldenData
              golden-data (.newInstance GoldenData)
              _ (set! (.root golden-data) (:root global))
              _ (set! (.mode golden-data) (:scale global))
              _ (set! (.chordSequence golden-data) chord-sequence)
              _ (set! (.stutter golden-data) 0)
              _ (set! (.bpm golden-data) (:bpm global))
              _ (set! (.chordDuration golden-data) 4)
              
              ;; Add lines
              _ (doseq [line lines]
                  (let [MidiInstrumentContext (load-java-class "haxe.root.MidiInstrumentContext")
                        constructor (.getConstructor MidiInstrumentContext 
                                                    (into-array Class [Integer/TYPE Integer/TYPE Double/TYPE Integer/TYPE]))
                        channel-val (int (:channel line))
                        velocity-val (int (:velocity line))
                        args (make-array Object 4)]
                    (aset args 0 (Integer/valueOf channel-val))
                    (aset args 1 (Integer/valueOf velocity-val))
                    (aset args 2 (Double/valueOf 0.8))
                    (aset args 3 (Integer/valueOf 0))
                    (let [midi-context (.newInstance constructor args)]
                      (.addLine golden-data (:pattern line) midi-context))))
              
              ;; Create line generators and generate notes
              line-count (.length (.lines golden-data))
              generators (make-array ILineGenerator line-count)
              _ (dotimes [i line-count]
                  (aset generators i (.makeLineGenerator golden-data i)))
              
              ;; Generate notes for each line
              line-notes (into {}
                              (for [[i line] (map-indexed vector lines)]
                                (let [generator (aget generators i)
                                      notes-array (.generateNotes generator 0)
                                      notes-list (for [j (range (.length notes-array))]
                                                   (let [note (clojure.lang.Reflector/invokeInstanceMethod notes-array "__get" (object-array [j]))]
                                                     {:midinote (.getMidiNoteValue note)
                                                      :start (.getStartTime note)
                                                      :duration (.getLength note)
                                                      :velocity (:velocity line)}))]
                                  [(str (:channel line)) notes-list])))
              
              ;; Create the JSON structure
              json-data {:meta {:bpm (:bpm global)
                               :root (:root global)
                               :mode (case (:scale global)
                                       0 "major"
                                       1 "minor"
                                       2 "harmonicMinor"
                                       3 "melodicMinor")
                               :chordSequence chord-sequence}
                        :lines line-notes}
              
              ;; Convert to JSON string
              json-string (json/write-str json-data {:pretty true})]
          {:success true
           :code [json-string]
           :notes "Generated JSON note data using GoldenPond library"
           :warning ""})))
    (catch Exception e
      (println "Exception in compile-to-json:" (.getMessage e))
      (.printStackTrace e)
      {:success false
       :code [""]
       :notes "Error during JSON compilation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn get-metadata []
  {:name "goldenpond"
   :type :java-jar
   :description "A DSL for generating musical compositions using the GoldenPond library"
   :version "1.0.0"
   :author "DSL MCP Team"
   :jar-file "goldenpond.jar"})

(defn get-plugin [tag-path load-fns]
  (let [dslname "goldenpond"
        load-java-class (:load-java-class load-fns)]

    {:metadata (get-metadata)
     :grammar
     {:rules (parse-grammar-rules grammar)
      :start "Music"}
     :targets
     {"summary"
      {:description "Generate GoldenData summary using GoldenPond library"
       :compile-fn (fn [s] (compile-to-summary s tag-path load-java-class))
       :header-fn (fn []
                    {:success true
                     :code "GoldenPond Composition Summary
This header provides information about the GoldenData summary format.

The summary includes:
- Root note and scale information
- Chord sequence configuration
- Line patterns and instrument settings
- Generated note counts and timing information

The summary is generated by creating a GoldenData object,
configuring it with the DSL parameters, and calling toString()."
                     :notes "Information about GoldenData summary format"
                     :warning "The summary is generated using the GoldenPond library"})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"GoldenData" code))
                                    (conj "Missing GoldenData reference")
                                    (not (re-find #"root" code))
                                    (conj "Missing root note information")
                                    (not (re-find #"chord" code))
                                    (conj "Missing chord sequence information")
                                    (not (re-find #"line" code))
                                    (conj "Missing line information")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required GoldenData summary content"}))
       :prompts {
                 :compile "Compiles GoldenPond DSL input into a GoldenData summary.

Arguments:
- dsl: The DSL input in the format:
  Root Scale BPM
  ChordSequence
  Channel Velocity Pattern
  Channel Velocity Pattern
  ...

Example:
Input: 
48 Major 120
71,76,72,75,71,76,72,75i
0 100 5/8 c 1
1 100 7/12 > 2
2 100 4/8 1 4

Output:
GoldenData summary showing:
- Root note and scale configuration
- Chord sequence details
- Line patterns and instrument settings
- Generated composition statistics

Notes:
- The summary is generated by creating a GoldenData object
- The object is configured with the DSL parameters
- toString() is called to get the summary
- This is the summary target implementation of the GoldenPond DSL"
                 :header "Gets information about the GoldenData summary format.

Example Output:
GoldenPond Composition Summary
This header provides information about the GoldenData summary format.

Notes:
- The summary includes root note, scale, chord sequence, and line information
- Generated by calling toString() on a configured GoldenData object
- This is the summary target implementation of the GoldenPond DSL header"
                 :eyeball "Performs sanity checks on generated GoldenData summary.

Checks:
- GoldenData reference is present
- Root note information is included
- Chord sequence information is present
- Line information is included

Example:
Input: Generated GoldenData summary
Output: Status and any issues found

Notes:
- Ensures summary contains all required GoldenData information
- Verifies the summary was properly generated
- This is the summary target implementation of the GoldenPond DSL eyeball function"
                 }
       }
      "json"
      {:description "Generate JSON note data using GoldenPond library"
       :compile-fn (fn [s] (compile-to-json s tag-path load-java-class))
       :header-fn (fn []
                    {:success true
                     :code "GoldenPond JSON Note Data Format
This header provides information about the JSON note data format.

The JSON output includes:
- Meta section with BPM, root note, mode, and chord sequence
- Lines section with channel numbers as keys
- Note arrays with midinote, start time, duration, and velocity

Example format:
{
  \"meta\": {
    \"bpm\": 120,
    \"root\": 48,
    \"mode\": \"minor\",
    \"chordSequence\": \"71,76,72,75,71,76,72,75i\"
  },
  \"lines\": {
    \"0\": [
      {\"midinote\": 60, \"start\": 0.0, \"duration\": 0.5, \"velocity\": 100}
    ]
  }
}

The JSON is generated by creating a GoldenData object,
configuring it with the DSL parameters, generating actual notes,
and converting the note data to JSON format."
                     :notes "Information about JSON note data format"
                     :warning "The JSON contains actual generated note data from GoldenPond library"})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"meta" code))
                                    (conj "Missing meta section")
                                    (not (re-find #"lines" code))
                                    (conj "Missing lines section")
                                    (not (re-find #"midinote" code))
                                    (conj "Missing midinote field")
                                    (not (re-find #"start" code))
                                    (conj "Missing start time field")
                                    (not (re-find #"duration" code))
                                    (conj "Missing duration field")
                                    (not (re-find #"velocity" code))
                                    (conj "Missing velocity field")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required JSON note data structure"}))
       :prompts {
                 :compile "Compiles GoldenPond DSL input into JSON note data.

Arguments:
- dsl: The DSL input in the format:
  Root Scale BPM
  ChordSequence
  Channel Velocity Pattern
  Channel Velocity Pattern
  ...

Example:
Input: 
48 Minor 120
71,76,72,75,71,76,72,75i
0 100 5/8 c 1
1 100 1.>. 2
2 100 4%8 1 4

Output:
JSON structure with:
- Meta section: BPM, root note, mode, chord sequence
- Lines section: Channel numbers as keys, arrays of notes
- Note objects: midinote, start time, duration, velocity

Notes:
- The JSON is generated by creating a GoldenData object
- The object is configured with the DSL parameters
- Line generators create actual note data
- Notes are converted to JSON format
- This is the JSON target implementation of the GoldenPond DSL"
                 :header "Gets information about the JSON note data format.

Example Output:
GoldenPond JSON Note Data Format
This header provides information about the JSON note data format.

Notes:
- The JSON includes meta section with composition settings
- Lines section contains channel numbers and note arrays
- Each note has midinote, start time, duration, and velocity
- This is the JSON target implementation of the GoldenPond DSL header"
                 :eyeball "Performs sanity checks on generated JSON note data.

Checks:
- Meta section is present
- Lines section is present
- Required note fields (midinote, start, duration, velocity) are included

Example:
Input: Generated JSON note data
Output: Status and any issues found

Notes:
- Ensures JSON contains all required sections and fields
- Verifies the JSON was properly generated
- This is the JSON target implementation of the GoldenPond DSL eyeball function"
                 }
       }
      }
     }))

;; Return both functions as a map - this is the last expression in the file
{:get-metadata get-metadata
 :get-plugin get-plugin} 