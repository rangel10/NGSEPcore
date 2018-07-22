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
package ngsep.alignments.io;

import java.io.PrintStream;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import ngsep.alignments.ReadAlignment;
import ngsep.sequences.QualifiedSequence;
import ngsep.sequences.QualifiedSequenceList;

/**
 * @author German Andrade
 * @author Jorge Duitama 
 *
 */
public class ReadAlignmentFileWriter {
	
	private SAMFileWriter writer;
	private SAMFileHeader samFileHeader;

	public ReadAlignmentFileWriter (QualifiedSequenceList sequences, PrintStream out)
	{
		samFileHeader = new SAMFileHeader();
		SAMSequenceDictionary sequenceDictionary = new SAMSequenceDictionary();
		for(QualifiedSequence seq:sequences) {
			SAMSequenceRecord sequenceRecord = new SAMSequenceRecord(seq.getName(), seq.getLength());
			sequenceDictionary.addSequence(sequenceRecord);
		}
		samFileHeader.setSequenceDictionary(sequenceDictionary);
//		writer= new SAMFileWriterFactory().makeBAMWriter(samFileHeader, true, out);
		writer= new SAMFileWriterFactory().makeBAMWriter(samFileHeader, false, out);
		

	}
	
	public void write(ReadAlignment readAlignment)
	{
		SAMRecord samRecord= new SAMRecord(samFileHeader);

		//QNAME
		samRecord.setReadName(readAlignment.getReadName());
		
		//FLAG
		samRecord.setFlags(readAlignment.getFlags());
		
		//RNAME
		samRecord.setReferenceName(readAlignment.getSequenceName());
		
		//POS
		samRecord.setAlignmentStart(readAlignment.getFirst());
		
		//MAPQ
		samRecord.setMappingQuality(readAlignment.getAlignmentQuality());
		
		//CIGAR
		// error because alignment is null 
		//samRecord.setCigarString(readAlignment.getCigarString());

		//RNEXT
		String mateReferenceName = readAlignment.getMateSequenceName()!=null?readAlignment.getMateSequenceName():SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
		samRecord.setMateReferenceName(mateReferenceName);

		
		//PNEXT
		samRecord.setMateAlignmentStart(readAlignment.getMateFirst());
		
		//TLEN
		samRecord.setInferredInsertSize(readAlignment.getInferredInsertSize());
		
		//SEQ
		String basesString = readAlignment.getReadCharacters().toString();
		samRecord.setReadBases(basesString.getBytes());
		
		//QUAL
		samRecord.setBaseQualityString(readAlignment.getQualityScores());
		
		writer.addAlignment(samRecord);
		
	}
}
