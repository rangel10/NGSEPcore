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
package ngsep.sequences;

/**
 * Performs pairwise alignment using the affine gap method.
 * Adapted from https://www.itu.dk/~sestoft/bsa/Match2.java
 * @author David Guevara
 */
public class PairwiseAlignmentAffineGap {
	
	int match;
	int openGap;
	int extGap;
	int mismatch;
	int[][] insertionScores;
	int[][] deletionScores;
	int[][] matchScores;
	
	public PairwiseAlignmentAffineGap(int match, int openGap, int extGap, int mismatch, int capacity) 
	{
		this.match = match;
		this.openGap = openGap;
		this.extGap = extGap;
		this.mismatch = mismatch;
		insertionScores = new int [capacity][capacity];
		deletionScores = new int [capacity][capacity];
		matchScores = new int [capacity][capacity];
	}
	
	public String[] getAlignment(String s1, String s2) 
	{		
		initMatrices(s1, s2);
	    calculateMatrices(s1, s2);	    
        return getAlignedStrings(s1, s2);
	}
	
	private void initMatrices(String s1, String s2)
	{
		if(insertionScores.length<s1.length()+1 || insertionScores[0].length < s2.length() +1 ) {
			System.out.println("Resizing matrices to "+(s1.length() + 1)+" - "+(s2.length() +1));
			insertionScores = new int[s1.length() + 1][s2.length() + 1];
			deletionScores = new int[s1.length() + 1][s2.length() + 1];
			matchScores = new int[s1.length() + 1][s2.length() + 1];
		}
		
		matchScores[0][0] = 0;
		for (int i = 1; i < insertionScores.length; i++) 
		{
			insertionScores[i][0] = - openGap - extGap * (i - 1);
	    	deletionScores[i][0] = s1.length() * -openGap * 1000;
	    	matchScores[i][0] = deletionScores[i][0];
	    }
	    for (int i = 1; i < insertionScores[0].length; i++) 
	    {
	    	deletionScores[0][i] = - openGap - extGap * (i - 1);
	        insertionScores[0][i] = s2.length() * -openGap * 1000;
	        matchScores[0][i] = insertionScores[0][i];
	    }
	}
	
	private void calculateMatrices(String s1, String s2)
	{
		for (int i = 1; i <= s1.length(); i++)
	    {
	    	for (int j = 1; j <= s2.length(); j++)
	    	{
	    		int matchScore = getMatchScore(s1.charAt(i - 1), s2.charAt(j - 1));
	    		matchScores[i][j] = Math.max(matchScores[i-1][j-1] + matchScore, Math.max(insertionScores[i-1][j-1] + matchScore, deletionScores[i-1][j-1] + matchScore));
	    		
	    		insertionScores[i][j] = Math.max(matchScores[i-1][j] - openGap, Math.max(insertionScores[i-1][j] - extGap, deletionScores[i-1][j] - openGap));
	    		
	    		deletionScores[i][j] = Math.max(matchScores[i][j-1] - openGap, Math.max(insertionScores[i][j-1] - openGap, deletionScores[i][j-1] - extGap));
	    		
	    	}
	    }
//		System.out.println("X");
//		printAlignmentMatrix(x, s1, s2);
//		System.out.println("Y");
//		printAlignmentMatrix(y, s1, s2);
//		System.out.println("M");
//		printAlignmentMatrix(m, s1, s2);
	}

	private int getMatchScore(char a, char b)
	{
		if (a == b)
			return match;
		else
			return -mismatch;
	}
	
	
	
	private String[] getAlignedStrings(String s1, String s2)
	{
		StringBuffer sb1 = new StringBuffer();
		StringBuffer sb2 = new StringBuffer();
		int i = s1.length();
		int j = s2.length();
		int k = 0;
	    int val = matchScores[i][j];
    	if (val < insertionScores[i][j]) {
    		k = 1;
    		val = insertionScores[i][j];
    	}
    	if (val < deletionScores[i][j]) {
    		k = 2;
    	}
		while(i>0 && j>0) {
			int matchScore = getMatchScore(s1.charAt(i - 1), s2.charAt(j - 1));
			if (k==0) {
				//Match matrix
				sb1.append(s1.charAt(i - 1));
				sb2.append(s2.charAt(j - 1));
				int score = matchScores[i][j]; 
				if(score == matchScores[i-1][j-1] + matchScore) k = 0;
	    		else if(score == insertionScores[i-1][j-1] + matchScore) k = 1;
	    		else if(score == deletionScores[i-1][j-1] + matchScore) k = 2;
	    		else throw new RuntimeException("Unexpected score error at "+i+" "+j);
				i--;
    			j--;
			} else if (k==1) {
				sb1.append(s1.charAt(i - 1));
				sb2.append(LimitedSequence.GAP_CHARACTER);
				int score = insertionScores[i][j];
				if(score == matchScores[i-1][j] - openGap) k = 0;
	    		else if(score == insertionScores[i-1][j] - extGap) k = 1;
	    		else if(score == deletionScores[i-1][j] - openGap) k = 2;
	    		else throw new RuntimeException("Unexpected score error at "+i+" "+j);
				i--;
			} else {
				sb1.append(LimitedSequence.GAP_CHARACTER);
				sb2.append(s2.charAt(j - 1));
				int score = deletionScores[i][j];
				if(score == matchScores[i][j-1] - openGap) k = 0;
	    		else if(score == insertionScores[i][j-1] - openGap) k = 1;
	    		else if(score == deletionScores[i][j-1] - extGap) k = 2;
	    		else throw new RuntimeException("Unexpected score error at "+i+" "+j);
				j--;
			}
        }
		while (i>0) {
			sb1.append(s1.charAt(i - 1));
			sb2.append(LimitedSequence.GAP_CHARACTER);
			i--;
		}
		while (j>0) {
			sb1.append(LimitedSequence.GAP_CHARACTER);
			sb2.append(s2.charAt(j - 1));
			j--;
		}
        String[] seqs = new String[2]; 
        seqs[0] = sb1.reverse().toString();
        seqs[1] = sb2.reverse().toString();
        return seqs;
	}
	
	public void printAlignmentMatrix(int[][] matrix, String s1, String s2)
	{
		System.out.print("\t-\t");
		for (int i = 0; i < s2.length(); i++) {
			System.out.print(s2.charAt(i) + "\t");
		}
		System.out.println();
		for (int i = 0; i < matrix.length; i++) {
			if(i == 0)
				System.out.print("-\t");
			else 
				System.out.print(s1.charAt(i - 1) + "\t");
		    for (int j = 0; j < matrix[i].length; j++) {
		        System.out.print(matrix[i][j] + "\t");
		    }
		    System.out.println();
		}
	}
}