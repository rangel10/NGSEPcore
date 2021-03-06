/*******************************************************************************
 * NGSEP - Next Generation Sequencing Experience Platform
 * Copyright 2016 Jorge Duitama
 *
 * This file is part of NGSEP.
 *
 *     NGSEP is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     NGSEP is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with NGSEP.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package ngsep.assembly;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import ngsep.sequences.DNAMaskedSequence;
import ngsep.sequences.KmersExtractor;
import ngsep.sequences.QualifiedSequence;
import ngsep.sequences.QualifiedSequenceList;
import ngsep.sequences.RawRead;
import ngsep.sequences.io.FastaSequencesHandler;
import ngsep.sequences.io.FastqFileReader;
import ngsep.main.CommandsDescriptor;
import ngsep.main.OptionValuesDecoder;
import ngsep.main.ProgressNotifier;

/**
 * @author Jorge Duitama
 * @author Juan Camilo Bojaca
 * @author David Guevara
 */
public class Assembler {

	// Constants for default values
	public static final byte INPUT_FORMAT_FASTQ=KmersExtractor.INPUT_FORMAT_FASTQ;
	public static final byte INPUT_FORMAT_FASTA=KmersExtractor.INPUT_FORMAT_FASTA;
	public static final int DEF_KMER_LENGTH = KmersExtractor.DEF_KMER_LENGTH;
	public static final int DEF_WINDOW_LENGTH = GraphBuilderMinimizers.DEF_WINDOW_LENGTH;
	public static final int DEF_MIN_READ_LENGTH = 5000;
	public static final int DEF_BP_HOMOPOLYMER_COMPRESSION = 0;
	public static final int DEF_NUM_THREADS = GraphBuilderMinimizers.DEF_NUM_THREADS;
	public static final String GRAPH_CONSTRUCTION_ALGORITHM_MINIMIZERS="Minimizers";
	public static final String GRAPH_CONSTRUCTION_ALGORITHM_FMINDEX="FMIndex";
	public static final String LAYOUT_ALGORITHM_MAX_OVERLAP="MaxOverlap";
	public static final String LAYOUT_ALGORITHM_KRUSKAL_PATH="KruskalPath";
	public static final String CONSENSUS_ALGORITHM_SIMPLE="Simple";
	public static final String CONSENSUS_ALGORITHM_POLISHING="Polishing";

	// Logging and progress
	private Logger log = Logger.getLogger(Assembler.class.getName());
	private ProgressNotifier progressNotifier = null;
	
	// Parameters
	private String inputFile = null;
	private String outputPrefix = null;
	private int kmerLength = DEF_KMER_LENGTH;
	private int windowLength = DEF_WINDOW_LENGTH;
	private int minReadLength = DEF_MIN_READ_LENGTH;
	private byte inputFormat = INPUT_FORMAT_FASTQ;
	private String graphFile = null;
	private String graphConstructionAlgorithm=GRAPH_CONSTRUCTION_ALGORITHM_MINIMIZERS;
	private String layoutAlgorithm=LAYOUT_ALGORITHM_KRUSKAL_PATH;
	private String consensusAlgorithm=CONSENSUS_ALGORITHM_SIMPLE;
	private boolean correctReads = false;
	private int bpHomopolymerCompression = DEF_BP_HOMOPOLYMER_COMPRESSION;
	private int numThreads = DEF_NUM_THREADS;
	
	// Get and set methods
	public Logger getLog() {
		return log;
	}
	public void setLog(Logger log) {
		this.log = log;
	}
	
	public ProgressNotifier getProgressNotifier() {
		return progressNotifier;
	}
	public void setProgressNotifier(ProgressNotifier progressNotifier) { 
		this.progressNotifier = progressNotifier;
	}
	
	public String getInputFile() {
		return inputFile;
	}
	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}
	public String getOutputPrefix() {
		return outputPrefix;
	}
	public void setOutputPrefix(String outputPrefix) {
		this.outputPrefix = outputPrefix;
	}
	
	public int getKmerLength() {
		return kmerLength;
	}
	public void setKmerLength(int kmerLength) {
		if(kmerLength<=0) throw new IllegalArgumentException("Kmer length should be a positive number");
		this.kmerLength = kmerLength;
	}
	public void setKmerLength(String value) {
		setKmerLength((int)OptionValuesDecoder.decode(value, Integer.class));
	}
	
	public int getWindowLength() {
		return windowLength;
	}
	public void setWindowLength(int windowLength) {
		if(windowLength<=0) throw new IllegalArgumentException("Window length should be a positive number");
		this.windowLength = windowLength;
	}
	public void setWindowLength(String value) {
		setWindowLength((int)OptionValuesDecoder.decode(value, Integer.class));
	}

	public byte getInputFormat() {
		return inputFormat;
	}
	public void setInputFormat(byte inputFormat) {
		if (inputFormat!=INPUT_FORMAT_FASTA && inputFormat != INPUT_FORMAT_FASTQ) {
			throw new IllegalArgumentException("Invalid input format "+inputFormat);
		}
		this.inputFormat = inputFormat;
	}
	public void setInputFormat(String value) {
		this.setInputFormat((byte) OptionValuesDecoder.decode(value, Byte.class));
	}
	
	public String getGraphFile() {
		return graphFile;
	}
	public void setGraphFile(String graphFile) {
		this.graphFile = graphFile;
	}
	
	public String getGraphConstructionAlgorithm() {
		return graphConstructionAlgorithm;
	}
	public void setGraphConstructionAlgorithm(String graphConstructionAlgorithm) {
		if(!GRAPH_CONSTRUCTION_ALGORITHM_FMINDEX.equals(graphConstructionAlgorithm) && !GRAPH_CONSTRUCTION_ALGORITHM_MINIMIZERS.equals(graphConstructionAlgorithm)) throw new IllegalArgumentException("Unrecognized graph construction algorithm "+graphConstructionAlgorithm);
		this.graphConstructionAlgorithm = graphConstructionAlgorithm;
	}
	
	public String getLayoutAlgorithm() {
		return layoutAlgorithm;
	}
	public void setLayoutAlgorithm(String layoutAlgorithm) {
		if(!LAYOUT_ALGORITHM_KRUSKAL_PATH.equals(layoutAlgorithm) && !LAYOUT_ALGORITHM_MAX_OVERLAP.equals(layoutAlgorithm)) throw new IllegalArgumentException("Unrecognized layout algorithm "+layoutAlgorithm);
		this.layoutAlgorithm = layoutAlgorithm;
	}
	public String getConsensusAlgorithm() {
		return consensusAlgorithm;
	}
	public void setConsensusAlgorithm(String consensusAlgorithm) {
		if(!CONSENSUS_ALGORITHM_SIMPLE.equals(consensusAlgorithm) && !CONSENSUS_ALGORITHM_POLISHING.equals(consensusAlgorithm)) throw new IllegalArgumentException("Unrecognized consensus algorithm "+consensusAlgorithm);
		this.consensusAlgorithm = consensusAlgorithm;
	}
	
	
	public int getBpHomopolymerCompression() {
		return bpHomopolymerCompression;
	}
	public void setBpHomopolymerCompression(int bpHomopolymerCompression) {
		this.bpHomopolymerCompression = bpHomopolymerCompression;
	}
	public void setBpHomopolymerCompression(String value) {
		this.setBpHomopolymerCompression((int) OptionValuesDecoder.decode(value, Integer.class));
	}
	
	public boolean isCorrectReads() {
		return correctReads;
	}
	public void setCorrectReads(boolean correctReads) {
		this.correctReads = correctReads;
	}
	public void setCorrectReads(Boolean correctReads) {
		this.setCorrectReads(correctReads.booleanValue());
	}
	public int getNumThreads() {
		return numThreads;
	}
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
	public void setNumThreads(String value) {
		this.setNumThreads((int) OptionValuesDecoder.decode(value, Integer.class));
	}
	
	public static void main(String[] args) throws Exception {
		Assembler instance = new Assembler ();
		CommandsDescriptor.getInstance().loadOptions(instance, args);
		instance.run();
	}

	public void run() throws IOException {
		logParameters();
		if(inputFile==null) throw new IOException("The input file with raw reads is required");
		if(outputPrefix==null) throw new IOException("An output prefix is required");
		run (inputFile, outputPrefix);
		log.info("Process finished");
	}
	private void logParameters() {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(os);
		out.println("Input file:"+ inputFile);
		out.println("Prefix for the output files:"+ outputPrefix);
		if (graphFile!=null) out.println("Load assembly graph from: "+graphFile);
		else out.println("Algorithm to build graph: "+graphConstructionAlgorithm);
		out.println("Algorithm to build layout: "+layoutAlgorithm);
		out.println("Algorithm to build consensus: "+consensusAlgorithm);
		if(bpHomopolymerCompression>0) out.println("Run homopolymer compression keeping at most "+bpHomopolymerCompression+" consecutive base pairs");
		//out.println("K-mer length: "+ kmerLength);
		//out.println("K-mer offset for FM-index: "+ kmerOffset);
		if (inputFormat == INPUT_FORMAT_FASTQ)  out.println("Fastq format");
		if (inputFormat == INPUT_FORMAT_FASTA)  out.println("Fasta format");
		log.info(os.toString());
	}

	public void run(String inputFile, String outputPrefix) throws IOException {
		List<QualifiedSequence> sequences = load(inputFile,inputFormat, minReadLength);
		long totalBp = 0;
		for(QualifiedSequence seq:sequences) totalBp+=seq.getLength();
		log.info("Loaded "+sequences.size()+" sequences. Total basepairs: "+totalBp);
		if(progressNotifier!=null && !progressNotifier.keepRunning(10)) return;
		AssemblyGraph graph;
		if(graphFile!=null) {
			graph = AssemblyGraph.load(sequences, graphFile);
		} else if (GRAPH_CONSTRUCTION_ALGORITHM_FMINDEX.equals(graphConstructionAlgorithm)) {
			GraphBuilderFMIndex gbIndex = new GraphBuilderFMIndex();
			gbIndex.setLog(log);
			gbIndex.setKmerLength(kmerLength);
			gbIndex.setNumThreads(numThreads);
			graph =  gbIndex.buildAssemblyGraph(sequences);
		} else {
			double [] compressionFactors =null;
			if (bpHomopolymerCompression>0) {
				compressionFactors = runHomopolymerCompression (sequences);
				log.info("Performed homopolymer compression");
			}
			
			GraphBuilderMinimizers builder = new GraphBuilderMinimizers();
			builder.setKmerLength(kmerLength);
			builder.setWindowLength(windowLength);
			//builder.setMinKmerPercentage(minKmerPercentage);
			builder.setNumThreads(numThreads);
			builder.setLog(log);
			graph = builder.buildAssemblyGraph(sequences,compressionFactors);
			if(bpHomopolymerCompression>0) {
				List<QualifiedSequence> originalSeqs = load(inputFile,inputFormat, minReadLength);
				log.info("Loaded original sequences to restore. Compressed sequences: "+sequences.size()+". Loaded: "+originalSeqs.size());
				for(int i=0;i<sequences.size();i++) {
					QualifiedSequence seq = sequences.get(i);
					CharSequence original = originalSeqs.get(i).getCharacters();
					seq.setCharacters(original);
				}
			}
		}
		log.info("Built assembly graph with "+graph.getVertices().size()+" vertices and "+graph.getEdges().size()+" edges");
		graph.updateVertexDegrees();
		log.info("Built assembly graph");
		
		if(progressNotifier!=null && !progressNotifier.keepRunning(50)) return;
		if(graphFile==null) {
			String outFileGraph = outputPrefix+".graph.gz";
			graph.save(outFileGraph);
			log.info("Saved graph to "+outFileGraph);
		}
		//graph.removeEdgesChimericReads();
		graph.filterEdgesAndEmbedded();
		log.info("Filtered graph. Vertices: "+graph.getVertices().size()+" edges: "+graph.getEdges().size());
		LayoutBuilder pathsFinder;
		if(LAYOUT_ALGORITHM_MAX_OVERLAP.equals(layoutAlgorithm)) {
			pathsFinder = new LayoutBuilderGreedyMaxOverlap();
			//LayoutBuilder pathsFinder = new LayoutBuilderGreedyMinCost();
		} else {
			pathsFinder= new LayoutBuilderKruskalPath();
			//LayourBuilder pathsFinder = new LayoutBuilderMetricMSTChristofides();
			//LayourBuilder pathsFinder = new LayoutBuilderModifiedKruskal();
		}
		pathsFinder.findPaths(graph);
		log.info("Layout complete. Paths: "+graph.getPaths().size());
		if(progressNotifier!=null && !progressNotifier.keepRunning(60)) return;
		ConsensusBuilder consensus;
		if(CONSENSUS_ALGORITHM_POLISHING.equals(consensusAlgorithm)) {
			ConsensusBuilderBidirectionalWithPolishing consensusP = new ConsensusBuilderBidirectionalWithPolishing();
			consensusP.setNumThreads(numThreads);
			if(correctReads) consensusP.setCorrectedReadsFile(outputPrefix+"_correctedReads.fa.gz");
			consensus = consensusP;
		} else {
			consensus = new ConsensusBuilderBidirectionalSimple();
		}
		List<QualifiedSequence> assembledSequences =  consensus.makeConsensus(graph);
		log.info("Built consensus");
		if(progressNotifier!=null && !progressNotifier.keepRunning(95)) return;
		FastaSequencesHandler handler = new FastaSequencesHandler();
		
		try (PrintStream out = new PrintStream(outputPrefix+".fa")) {
			handler.saveSequences(assembledSequences, out, 100);
		}
	}

	private double [] runHomopolymerCompression(List<QualifiedSequence> sequences) {
		double [] compressionFactors = new double[sequences.size()];
		for(int i=0;i<sequences.size();i++) {
			QualifiedSequence seq = sequences.get(i);
			compressionFactors[i] = compressHomopolymers(seq);
		}
		return compressionFactors;
	}
	private double compressHomopolymers(QualifiedSequence seq) {
		String seqStr = seq.getCharacters().toString();
		int n = seqStr.length();
		StringBuilder compressed = new StringBuilder(n);
		char c2 = 0;
		int homopolymerCount = 0;
		for (int i=0;i<n;i++) {
			char c = seqStr.charAt(i);
			if (c==c2) homopolymerCount++;
			else homopolymerCount = 1;
			if(homopolymerCount<=bpHomopolymerCompression) compressed.append(c);
			c2=c;
		}
		double answer = compressed.length();
		if(n>0) answer /=n;
		seq.setCharacters(new DNAMaskedSequence(compressed));
		return answer;
	}
	/**
	 * Load the sequences of the file
	 * 
	 * @param Filename the file path
	 * @return The sequences
	 * @throws IOException The file cannot opened
	 */
	public static List<QualifiedSequence> load(String filename, byte inputFormat, int minReadLength) throws IOException {
		List<QualifiedSequence> sequences;
		if (INPUT_FORMAT_FASTQ == inputFormat) sequences = loadFastq(filename,minReadLength);
		else if (INPUT_FORMAT_FASTA==inputFormat) sequences = loadFasta(filename, minReadLength);
		else throw new IOException("the file not is a fasta or fastq file: " + filename);
		Collections.sort(sequences, (l1, l2) -> l2.getLength() - l1.getLength());
		return sequences;
	}

	/**
	 * Load the sequences of the Fasta file
	 * @param filename the file path
	 * @return The sequences
	 * @throws IOException The file cannot opened
	 */
	private static List<QualifiedSequence> loadFasta(String filename, int minReadLength) throws IOException {
		FastaSequencesHandler handler = new FastaSequencesHandler();
		QualifiedSequenceList seqsQL = handler.loadSequences(filename);
		List<QualifiedSequence> answer = new ArrayList<QualifiedSequence>();
		for(QualifiedSequence seq:seqsQL) {
			if(seq.getLength()>=minReadLength) answer.add(seq);
		}
		return answer;
	}

	/**
	 * Load the sequences of the Fastq file
	 * 
	 * @param Filename the file path
	 * @return The sequences
	 * @throws IOException The file cannot opened
	 */
	private static List<QualifiedSequence> loadFastq(String filename, int minReadLength) throws IOException {
		List<QualifiedSequence> sequences = new ArrayList<>();
		try (FastqFileReader reader = new FastqFileReader(filename)) {
			reader.setSequenceType(DNAMaskedSequence.class);
			//TODO: Option to load quality scores
			reader.setLoadMode(FastqFileReader.LOAD_MODE_WITH_NAME);
			Iterator<RawRead> it = reader.iterator();
			while (it.hasNext()) {
				RawRead read = it.next();
				DNAMaskedSequence characters = (DNAMaskedSequence) read.getCharacters();
				if(characters.length()>=minReadLength) sequences.add(new QualifiedSequence(read.getName(), characters));
			}
		}
		return sequences;
	}
}
