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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.logging.Logger;

import ngsep.sequences.DNAMaskedSequence;
import ngsep.sequences.FMIndex;
import ngsep.sequences.UngappedSearchHit;
import ngsep.sequences.KmersExtractor;
import ngsep.sequences.KmersMap;
import ngsep.sequences.QualifiedSequence;

/**
 * @author Jorge Duitama
 * @author Juan Camilo Bojaca
 * @author David Guevara
 */
public class GraphBuilderFMIndex implements GraphBuilder {
	private Logger log = Logger.getLogger(GraphBuilderFMIndex.class.getName());
	
	private static final int TIMEOUT_SECONDS = 30;

	private int kmerLength = 15;
	private int kmerOffset = 5;
	private int numThreads = 1;
	
	private KmersMap kmersMap;
	

	
	private static int idxDebug = -1;
	
	/**
	 * @return the log
	 */
	public Logger getLog() {
		return log;
	}
	
	/**
	 * @param log the log to set
	 */
	public void setLog(Logger log) {
		this.log = log;
	}

	public int getKmerLength() {
		return kmerLength;
	}

	public void setKmerLength(int kmerLength) {
		this.kmerLength = kmerLength;
	}

	public int getNumThreads() {
		return numThreads;
	}

	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}

	@Override
	public AssemblyGraph buildAssemblyGraph(List<QualifiedSequence> sequences) {
		
		AssemblyGraph graph = new AssemblyGraph(sequences);
		log.info("Created graph vertices. Edges: "+graph.getEdges().size());
		
		//Kmers map construction
		if(kmersMap==null) {
			log.info("Extracting kmers map");
			KmersExtractor extractor = new KmersExtractor();
			extractor.setKmerLength(kmerLength);
			extractor.processQualifiedSequences(sequences);
			kmersMap = extractor.getKmersMap();
			log.info("Extracting kmers map. Kmers: "+kmersMap.size());
		}
		// Create FM-Index
		FMIndex fmIndex = new FMIndex();
		// TODO: Set tally distance and suffix fraction
		fmIndex.loadQualifiedSequences(sequences);
		
		log.info("Created FM-Index");
		KmerHitsAssemblyEdgesFinder edgesFinder = new KmerHitsAssemblyEdgesFinder(graph);
		ThreadPoolExecutor pool = new ThreadPoolExecutor(numThreads, numThreads, TIMEOUT_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	
		for (int seqId = 0; seqId < sequences.size(); seqId++) {
			CharSequence seq = sequences.get(seqId).getCharacters();
			if(numThreads==1) {
				processSequence(edgesFinder, fmIndex, seqId, seq);
				if ((seqId+1)%100==0) log.info("Processed "+(seqId+1) +" sequences. Number of edges: "+graph.getEdges().size()+ " Embedded: "+graph.getEmbeddedCount());
				continue;
			}
			Runnable task = new GraphBuilderFMIndexProcessSequenceTask(this, edgesFinder, fmIndex, seqId, seq);
			pool.execute(task);
		}
		pool.shutdown();
		try {
			pool.awaitTermination(2*sequences.size(), TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
    	if(!pool.isShutdown()) {
			throw new RuntimeException("The ThreadPoolExecutor was not shutdown after an await Termination call");
		}
		log.info("Built graph. Edges: "+graph.getEdges().size()+" Embedded: "+graph.getEmbeddedCount()+" Prunning embedded sequences");
		graph.pruneEmbeddedSequences();
		log.info("Prunned graph. Edges: "+graph.getEdges().size());
		
		return graph;
	}

	void processSequence(KmerHitsAssemblyEdgesFinder finder, FMIndex fmIndex, int seqId, CharSequence seq) {
		updateGraph(finder, seqId, seq, false, fmIndex);
		CharSequence complement = DNAMaskedSequence.getReverseComplement(seq);
		updateGraph(finder, seqId, complement, true, fmIndex);
	}


	private void updateGraph(KmerHitsAssemblyEdgesFinder finder, int queryIdx, CharSequence query, boolean queryRC, FMIndex fmIndex) {
		Map<Integer,String> sequenceKmersMap = KmersExtractor.extractKmersAsMap(query.toString(), kmerLength, 1, false, false, true);
		//Search kmers using the FM index
		if(sequenceKmersMap.size()==0) return;
		Map<Integer,List<UngappedSearchHit>> kmerHitsMap = new HashMap<Integer, List<UngappedSearchHit>>();
		int i=0;
		while (i<query.length()) {
			String kmer = sequenceKmersMap.get(i);
			if (kmer==null) {
				i+=kmerLength;
				continue;
			}
			int globalCount = kmersMap.getCount(kmer);
			//TODO: Make better parameter
			if(globalCount==1 || globalCount>100) {
				i+=kmerLength;
				continue;
			}
			List<UngappedSearchHit> kmerHits=fmIndex.exactSearch(kmer);
			if(idxDebug==queryIdx) System.out.println("GraphBuilderFMIndex. Query: "+queryIdx+" "+queryRC+" Kmer: "+kmer+" kmer count: "+globalCount+" kmer hits: "+kmerHits.size());
			for(UngappedSearchHit hit:kmerHits) {
				if(idxDebug==queryIdx) System.out.println("Kmer start: "+i+" Next hit: "+hit.getSequenceIdx()+": "+hit.getStart());
				hit.setQueryIdx(i);
				List<UngappedSearchHit> kmerHitsList = kmerHitsMap.computeIfAbsent(hit.getSequenceIdx(), l->new ArrayList<UngappedSearchHit>());
				kmerHitsList.add(hit);
			}
			i+=kmerOffset;
		}
		finder.updateGraphWithKmerHitsMap(queryIdx, query, queryRC, 1, sequenceKmersMap.size(), kmerHitsMap);
	}
}
class GraphBuilderFMIndexProcessSequenceTask implements Runnable {
	private GraphBuilderFMIndex parent;
	private KmerHitsAssemblyEdgesFinder finder;
	private FMIndex fmIndex;
	private int sequenceId;
	private CharSequence sequence;
	
	
	
	
	public GraphBuilderFMIndexProcessSequenceTask(GraphBuilderFMIndex parent, KmerHitsAssemblyEdgesFinder finder, FMIndex fmIndex, int sequenceId, CharSequence sequence) {
		super();
		this.parent = parent;
		this.finder = finder;
		this.fmIndex = fmIndex;
		this.sequenceId = sequenceId;
		this.sequence = sequence;
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		parent.processSequence(finder, fmIndex, sequenceId, sequence);
		AssemblyGraph graph = finder.getGraph();
		if ((sequenceId+1)%100==0) parent.getLog().info("Processed "+(sequenceId+1) +" sequences. Number of edges: "+graph.getNumEdges()+ " Embedded: "+graph.getEmbeddedCount());
	}
	
	
}