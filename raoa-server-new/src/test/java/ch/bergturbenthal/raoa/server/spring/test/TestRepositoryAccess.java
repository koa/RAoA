package ch.bergturbenthal.raoa.server.spring.test;

import java.io.File;
import java.io.IOException;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

@Slf4j
public class TestRepositoryAccess {
	public static void main(String args) throws IOException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		Repository repository = builder//.setWorkTree(new File("/data/heap/data/photos/.meta/"))
		  .readEnvironment() // scan environment GIT_* variables
		  .findGitDir(new File("/data/heap/data/photos/Pferde/Turniere/2015/WM Breda 2015/")) // scan up the file system tree
		  .build();
		ObjectId head = repository.resolve("HEAD"); 
		log.info("Head: "+head);
		@Cleanup
		RevWalk walk = new RevWalk(repository);
		RevCommit commit = walk.parseCommit(head);
		
		log.info("Commit: "+commit);
		RevTree tree = commit.getTree();
		log.info("Tree: "+tree);
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(tree);
		int count=0;
		long totalSize=0;
		while(treeWalk.next()) {
			if(treeWalk.isSubtree())
				continue;
			count+=1;
			//log.info("Path: "+treeWalk.getPathString()); 
//			ObjectReader objectReader = treeWalk.getObjectReader();
			ObjectId objectId = treeWalk.getObjectId(0);
			long objectSize = repository.getObjectDatabase().newReader().getObjectSize(objectId, Constants.OBJ_BLOB);
			//ObjectLoader objectLoader = repository.open(objectId);
			
//			log.info("Object id: "+objectId);
			//long objectSize = objectLoader.getSize();
			totalSize+=objectSize;
			//log.info(treeWalk.getPathString()+": "+objectSize);
			
		}		
		log.info("Count: "+count+", Size: "+totalSize); 
	}
}
