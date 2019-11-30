package ngsep.alignments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ngsep.genome.GenomicRegion;

public class KmerAlignmentCluster implements GenomicRegion {
	private CharSequence query;
	private List<ReadAlignment> alns=new ArrayList<>();
	private int sequenceIdx;
	private String sequenceName;
	private int first;
	private int last;
	private Set<Integer> kmerNumbers = new HashSet<>();
	private boolean allConsistent = true;
	private boolean repeatedNumber = false;
	private boolean lastAlnPresent = false;

	public KmerAlignmentCluster(CharSequence query, ReadAlignment aln) {
		this.query = query;
		sequenceIdx = aln.getSequenceIndex();
		sequenceName = aln.getSequenceName();
		int kmerQueryStart = aln.getReadNumber();
		first = aln.getFirst() - kmerQueryStart;
		last = aln.getFirst()+(query.length()-kmerQueryStart-1);
		alns.add(aln);
		kmerNumbers.add(kmerQueryStart);
		lastAlnPresent = kmerQueryStart+aln.length()==query.length();
	}

	@Override
	public String getSequenceName() {
		return sequenceName;
	}
	

	/**
	 * @return the sequenceIdx
	 */
	public int getSequenceIdx() {
		return sequenceIdx;
	}

	@Override
	public int getFirst() {
		return first;
	}

	@Override
	public int getLast() {
		return last;
	}

	@Override
	public int length() {
		return last-first+1;
	}

	@Override
	public boolean isPositiveStrand() {
		return true;
	}

	@Override
	public boolean isNegativeStrand() {
		return false;
	}
	public boolean addAlignment(ReadAlignment aln) {
		int kmerQueryStart = aln.getReadNumber();
		int estFirst = aln.getFirst() - kmerQueryStart;
		int estLast = aln.getFirst()+(query.length()-kmerQueryStart-1);
		//System.out.println("Previous coords: "+first+"-"+last+" next cords: "+estFirst+"-"+estLast);
		if(first > estLast || last < estFirst) return false;
		if(first != estFirst) allConsistent = false;
		if(last != estLast) allConsistent = false;
		if(kmerNumbers.contains(kmerQueryStart)) repeatedNumber = true;
		else kmerNumbers.add(kmerQueryStart);
		if(kmerQueryStart+aln.length()==query.length()) lastAlnPresent=true;
		if(first>estFirst) first = estFirst;
		if(last<estLast) last = estLast;
		alns.add(aln);
		return true;	
	}

	/**
	 * @return the query
	 */
	public CharSequence getQuery() {
		return query;
	}

	/**
	 * 
	 * @return the kmerNumbers
	 */
	public int getNumDifferentKmers() {
		return kmerNumbers.size();
	}

	/**
	 * @return the allConsistent
	 */
	public boolean isAllConsistent() {
		return allConsistent;
	}

	/**
	 * @return the repeatedNumber
	 */
	public boolean isRepeatedNumber() {
		return repeatedNumber;
	}

	public boolean isFirstAlnPresent() {
		return kmerNumbers.contains(0);
	}
	/**
	 * @return the lastAlnPresent
	 */
	public boolean isLastAlnPresent() {
		return lastAlnPresent;
	}
	public static Collection<KmerAlignmentCluster> clusterSequenceKmerAlns(CharSequence query, List<ReadAlignment> sequenceAlns) {
		Collection<KmerAlignmentCluster> answer = new ArrayList<>();
		//System.out.println("Alns to cluster: "+sequenceAlns.size());
		KmerAlignmentCluster cluster=null;
		for(ReadAlignment aln:sequenceAlns) {
			if(cluster==null || !cluster.addAlignment(aln)) {
				cluster = new KmerAlignmentCluster(query, aln);
				answer.add(cluster);
			}
		}
		return answer;
	}
}