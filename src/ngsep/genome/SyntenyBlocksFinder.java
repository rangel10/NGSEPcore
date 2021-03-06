package ngsep.genome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class SyntenyBlocksFinder {
	
	private int minBlockLength;
	
	private int maxDistance;
	
	private List<HomologyEdge> homologyEdges;
	
	private List<SyntenyEdge> orthologsEdges = new ArrayList<>();
	
	private HashMap<HomologyEdge, List<SyntenyEdge>> orthologsConnected = new HashMap<>();
	
	private HashMap<HomologyEdge, Integer> orthologsWeightVertices = new HashMap<>();
	
	private HashMap<HomologyEdge, List<SyntenyEdge>> orthologsTraceback = new HashMap<>();
	
	private List<SyntenyBlock> orthologsSyntenyBlocks = new ArrayList<>();
	
	private List<SyntenyEdge> paralogsEdges = new ArrayList<>();
	
	private HashMap<HomologyEdge, List<SyntenyEdge>> paralogsConnected = new HashMap<>();
	
	private HashMap<HomologyEdge, Integer> paralogsWeightVertices = new HashMap<>();
	
	private HashMap<HomologyEdge, List<SyntenyEdge>> paralogsTraceback = new HashMap<>();
	
	private List<SyntenyBlock> paralogsSyntenyBlocks = new ArrayList<>();
	
	private AnnotatedReferenceGenome genome1;
	
	private AnnotatedReferenceGenome genome2;
	
	public static final int PARALOGS = 1;
	
	public static final int ORTHOLOGS = 2;
	
	public SyntenyBlocksFinder(int minBlockLength, int maxDistance, AnnotatedReferenceGenome g1, AnnotatedReferenceGenome g2, List<HomologyEdge> homologyEdges) {
		this.minBlockLength = minBlockLength;
		this.maxDistance = maxDistance;
		this.homologyEdges = homologyEdges;
		this.genome1 = g1;
		this.genome2 = g2;
	}
	
	public void resetData() {
		orthologsEdges = new ArrayList<>();
		
		orthologsConnected = new HashMap<>();
		
		orthologsWeightVertices = new HashMap<>();
		
		orthologsTraceback = new HashMap<>();
		
		
		paralogsEdges = new ArrayList<>();
		
		paralogsConnected = new HashMap<>();
		
		paralogsWeightVertices = new HashMap<>();
		
		paralogsTraceback = new HashMap<>();
		
	}
	
	public List<SyntenyBlock> findSyntenyBlocks(int homologyType) {
		
		List<String> visitedNames = new ArrayList<>();
		List<HomologyEdge> vertices = selectVertices(homologyType);
		for (HomologyEdge he : vertices) {
			String seqName = he.getQueryUnit().getSequenceName();
			if (!visitedNames.contains(seqName)) {
				List<HomologyEdge> chrvertices = new ArrayList<>();
				for (HomologyEdge vert : vertices) {
					String vertName = vert.getQueryUnit().getSequenceName();
					if (seqName.equals(vertName)) chrvertices.add(vert);
				}
				visitedNames.add(seqName);
				
				buildGraph(chrvertices, homologyType);
				initializeWeights(homologyType);
				traverseVertices(homologyType);

				List<SyntenyEdge> path = findMaximalWeight(homologyType);
				while (verifySyntenyBlock(path, homologyType)) {
					path = findMaximalWeight(homologyType);			
				}
				
				resetData();
			}
		}
		if (homologyType == ORTHOLOGS) {
			orthologsSyntenyBlocks = collapseOverlapped(orthologsSyntenyBlocks);
			return orthologsSyntenyBlocks;
		} else {
			paralogsSyntenyBlocks = collapseOverlapped(paralogsSyntenyBlocks);
			return paralogsSyntenyBlocks;
		}
	}
	
	private List<HomologyEdge> selectVertices(int homologyType) {
		List<HomologyEdge> vertices = new ArrayList<>();
		for (HomologyEdge he : homologyEdges) {
			HomologyUnit query = he.getQueryUnit();
			HomologyUnit subject = he.getSubjectUnit();
			boolean filter = false;
			if (homologyType == ORTHOLOGS) 
				filter = query.getGenomeId() != subject.getGenomeId();
			else
				filter = query.getGenomeId() == subject.getGenomeId();
			
			if(filter) {
//				HomologyUnit q = he.getQueryUnit();
//				HomologyUnit s = he.getSubjectUnit();
//				if (q.getGenomeId() == 2) {
//					vertices.add(new HomologyEdge(s, q, 0));
//				} else vertices.add(he);
				vertices.add(he);
			}
		}
		return vertices;
		
	}
	
	private void buildGraph(List<HomologyEdge> vertices, int homologyType) {
		vertices.sort((a,b)->compareUnits(a, b));
		for (HomologyEdge ve1 : vertices) {
			for (HomologyEdge ve2 : vertices) {
				if (!ve1.equals(ve2)) {
					HomologyUnit q1 = ve1.getQueryUnit();
					HomologyUnit s1 = ve1.getSubjectUnit();
					HomologyUnit q2 = ve2.getQueryUnit();
					HomologyUnit s2 = ve2.getSubjectUnit();
					boolean isSyntenic = q1.getLast() <= q2.getFirst() && s1.getLast() <= s2.getFirst() && q1.isPositiveStrand() == q2.isPositiveStrand();
					boolean notOverlapping = q2.getFirst()-q1.getLast() >= 0 && q2.getFirst()-q1.getLast() < maxDistance
							&& s2.getFirst()-s1.getLast() >= 0 && s2.getFirst()-s1.getLast() < maxDistance;
					if (isSyntenic && notOverlapping) { 
						SyntenyEdge se = new SyntenyEdge(ve1, ve2, s2.length());
						if (homologyType == ORTHOLOGS) {
							orthologsEdges.add(se);
							List<SyntenyEdge> con = orthologsConnected.getOrDefault(ve1, new ArrayList<>());
							con.add(se);
							orthologsConnected.put(ve1, con);							
						} else {
							paralogsEdges.add(se);
							List<SyntenyEdge> con = paralogsConnected.getOrDefault(ve1, new ArrayList<>());
							con.add(se);
							paralogsConnected.put(ve1, con);
						}
					}
				}
			}
		}
	}
	
	
	private void initializeWeights(int homologyType) {
		if (homologyType == ORTHOLOGS) {
			for (SyntenyEdge se : orthologsEdges) {
				HomologyEdge vi = se.getSource();
				HomologyEdge vj = se.getTarget();
				orthologsWeightVertices.put(vi, Math.abs(vi.getSubjectUnit().length()));
				orthologsWeightVertices.put(vj, Math.abs(vj.getSubjectUnit().length()));
			}	
		} else {
			for (SyntenyEdge se : paralogsEdges) {
				HomologyEdge vi = se.getSource();
				HomologyEdge vj = se.getTarget();
				paralogsWeightVertices.put(vi, Math.abs(vi.getSubjectUnit().length()));
				paralogsWeightVertices.put(vj, Math.abs(vj.getSubjectUnit().length()));
			}
		}
	}
	
	private void traverseVertices(int homologyType) {
		if (homologyType == ORTHOLOGS) {
			List<HomologyEdge> hedges = new ArrayList<>(orthologsConnected.keySet());
			hedges.sort((a,b)->compareUnits(a, b));
			for (HomologyEdge he : hedges) {
				List<SyntenyEdge> cons = orthologsConnected.get(he);
				for (SyntenyEdge se : cons) {
					HomologyEdge target = se.getTarget();
					int newWeight = se.getWeight() + orthologsWeightVertices.get(he);
					int currentWeight = orthologsWeightVertices.get(target);
					if (newWeight > currentWeight) {
						orthologsWeightVertices.put(target, newWeight);
						List<SyntenyEdge> parents = orthologsTraceback.getOrDefault(he, new ArrayList<SyntenyEdge>());
						parents.add(se);
						orthologsTraceback.put(target, parents);
					}				
				}
			}			
		} else {
			List<HomologyEdge> hedges = new ArrayList<>(paralogsConnected.keySet());
			hedges.sort((a,b)->compareUnits(a, b));
			for (HomologyEdge he : hedges) {
				List<SyntenyEdge> cons = paralogsConnected.get(he);
				for (SyntenyEdge se : cons) {
					//HomologyEdge vi = se.getVi();
					HomologyEdge target = se.getTarget();
					int newWeight = se.getWeight() + paralogsWeightVertices.get(he);
					int currentWeight = paralogsWeightVertices.get(target);
					if (newWeight > currentWeight) {
						paralogsWeightVertices.put(target, newWeight);
						List<SyntenyEdge> parents = paralogsTraceback.getOrDefault(he, new ArrayList<SyntenyEdge>());
						parents.add(se);
						paralogsTraceback.put(target, parents);
					}				
				}
			}
		}
		
	}
	
	private List<SyntenyEdge> findMaximalWeight(int homologyType) {
		HomologyEdge maxVertex = null;
		int maxWeight = 0;
		HashMap<HomologyEdge, Integer> weightV;
		HashMap<HomologyEdge, List<SyntenyEdge>> traceback;
		if (homologyType == ORTHOLOGS) {
			weightV = orthologsWeightVertices;
			traceback = orthologsTraceback;
		}
		else {
			weightV = paralogsWeightVertices;
			traceback = paralogsTraceback;
		}
		for (HomologyEdge v : weightV.keySet()) {
			int weight = weightV.get(v);
			if (weight > maxWeight) {
				maxVertex = v;
				maxWeight = weight;
			}
		}
		return traceback.getOrDefault(maxVertex, new ArrayList<SyntenyEdge>());
	}
	
	private boolean verifySyntenyBlock(List<SyntenyEdge> path, int homologyType) {

		HashMap<HomologyEdge, Integer> weightV;
		List<SyntenyBlock> syntenyBlocks;
		if (homologyType == ORTHOLOGS) {
			weightV = orthologsWeightVertices;
			syntenyBlocks = orthologsSyntenyBlocks;
		} else {
			weightV = paralogsWeightVertices;
			syntenyBlocks = paralogsSyntenyBlocks;
		}
		if (path.isEmpty()) return false;
		int length = 0;
		for (SyntenyEdge se : path) {
			length += se.getSource().getQueryUnit().length() + se.getTarget().getQueryUnit().length();
		}
		
		//check length and add synteny block
		if (length >= minBlockLength) {
			HomologyUnit firstUnit = path.get(0).getSource().getQueryUnit();
			int[] firstAndLast = getFirstAndLast(path);
			int first = firstAndLast[0];
			int last = firstAndLast[1];
			SyntenyBlock sb = new SyntenyBlock(firstUnit.getSequenceName(), first, last, genome1, genome2, path);
			syntenyBlocks.add(sb);
		} 
		// remove vertices
		for (SyntenyEdge se : path) {
			HomologyEdge s = se.getSource();
			HomologyEdge t = se.getTarget();
			weightV.remove(s);
			weightV.remove(t);
		}
		return true;

	}
	
	private int compareUnits(HomologyEdge a, HomologyEdge b) {
		int aStart = a.getQueryUnit().getFirst();
		int bStart = b.getQueryUnit().getFirst();
		return aStart - bStart;
	}
	
	private int[] getFirstAndLast(List<SyntenyEdge> edges) {
		int first = Integer.MAX_VALUE;
		int last = 0;
		for (SyntenyEdge se : edges) {
			HomologyUnit source = se.getSource().getQueryUnit();
			HomologyUnit target = se.getTarget().getQueryUnit();
			if (source.getFirst() < first)
				first = source.getFirst();
			else if (target.getFirst() < first)
				first = target.getFirst();
			else if (source.getLast() > last)
				last = source.getLast();
			else if (target.getLast() > last)
				last = target.getLast();
		}
		return new int[]{first,last};
	}
	
	private List<SyntenyBlock> collapseOverlapped(List<SyntenyBlock> prev) {
		List<SyntenyBlock> temp = prev;
		while (selfOverlap(temp)) {
			List<SyntenyBlock> collapsed = new ArrayList<>();
			int i = 0;
			while (!temp.isEmpty()) {
				List<SyntenyBlock> toJoin = new ArrayList<>();
				SyntenyBlock actual = temp.get(i);
				toJoin.add(actual);
				for (int j = 0; j < temp.size(); j++) {
					SyntenyBlock other = temp.get(j);
					if (i != j && actual.getSequenceName().equals(other.getSequenceName()) && areOverlapping(actual, other)) {
						toJoin.add(other);
					}
				}
				temp.removeAll(toJoin);
				collapsed.add(joinCollapsed(toJoin));
			}
			temp = collapsed;
		}
		return temp;
	}
	
	private SyntenyBlock joinCollapsed(List<SyntenyBlock> toJoin) {
		if (toJoin.size() == 1)
			return toJoin.get(0);
		int minStart = Integer.MAX_VALUE;
		int maxEnd = 0;
		List<SyntenyEdge> joinedHomologies = new ArrayList<>();
		for (SyntenyBlock e : toJoin) {
			if (e.getFirst() < minStart)
				minStart = e.getFirst();
			if (e.getLast() > maxEnd)
				maxEnd = e.getLast();
			joinedHomologies.addAll(e.getHomologies());
		}
		SyntenyBlock joined = new SyntenyBlock(toJoin.get(0).getSequenceName(), minStart, maxEnd,genome1, genome2, joinedHomologies);
		return joined;
	}
	
	public boolean selfOverlap(List<SyntenyBlock> test) {
		for (int i = 0; i < test.size(); i++) {
			SyntenyBlock actual = test.get(i);
			for (int j = 0; j < test.size(); j++) {
				SyntenyBlock other = test.get(j);
				if (i != j && actual.getSequenceName().equals(other.getSequenceName()) && areOverlapping(actual, other)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean areOverlapping(SyntenyBlock e1, SyntenyBlock e2) {
		long start1 = e1.getFirst();
		long end1 = e1.getLast();
		long start2 = e2.getFirst();
		long end2 = e2.getLast();
		return (start1 >= start2 && start1 <= end2) || (end1 <= end2 && end1 >= start2)
				|| (start2 >= start1 && start2 <= end1) || (end2 <= end1 && end2 >= start1);
	}

	
}
