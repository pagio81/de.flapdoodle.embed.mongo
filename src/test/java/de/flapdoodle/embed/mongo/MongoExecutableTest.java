/**
 * Copyright (C) 2011
 *   Michael Mosmann <michael@mosmann.de>
 *   Martin Jöhren <m.joehren@googlemail.com>
 *
 * with contributions from
 * 	konstantin-ba@github,Archimedes Trajano	(trajano@github)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.flapdoodle.embed.mongo;

import static de.flapdoodle.embed.mongo.TestUtils.getCmdOptions;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.transitions.MongodProcessArguments;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.progress.ProgressListeners;
import de.flapdoodle.embed.process.io.progress.StandardConsoleProgressListener;
import de.flapdoodle.embed.process.transitions.Starter;
import de.flapdoodle.embed.process.types.RunningProcess;
import de.flapdoodle.reverse.StateID;
import de.flapdoodle.reverse.Transition;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.Transitions;
import de.flapdoodle.types.Try;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

import de.flapdoodle.embed.mongo.config.Defaults;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.RuntimeConfig;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * Integration test for starting and stopping MongodExecutable
 * 
 * @author m.joehren
 */
//CHECKSTYLE:OFF
public class MongoExecutableTest {

	private static final Logger logger = LoggerFactory.getLogger(MongoExecutableTest.class.getName());

	@Test
	public void testStartStopTenTimesWithTransitions() throws UnknownHostException {
		List<Transition<?>> transitions = Defaults.transitionsFor(MongodProcessArguments.withDefaults(), MongodArguments.defaults(), Version.Main.PRODUCTION);

		String dot = Transitions.edgeGraphAsDot("mongod", Transitions.asGraph(transitions));
		System.out.println("---------------------");
		System.out.println(dot);
		System.out.println("---------------------");

		try (ProgressListeners.RemoveProgressListener ignored = ProgressListeners.setProgressListener(new StandardConsoleProgressListener())) {

			for (int i=0;i<2;i++) {
				try (TransitionWalker.ReachedState<RunningMongodProcess> running = TransitionWalker.with(transitions)
					.initState(StateID.of(RunningMongodProcess.class))) {

					try (MongoClient mongo = new MongoClient(running.current().getServerAddress())) {
						MongoDatabase db = mongo.getDatabase("test");
						MongoCollection<Document> col = db.getCollection("testCol");
						col.insertOne(new Document("testDoc", new Date()));
						System.out.println("could store doc in database...");
					}
				}
			}
		}
	}

	@Test
	public void testStartStopTenTimesWithNewMongoExecutable() throws IOException {
		boolean useMongodb = true;
		int loops = 10;

		final Version.Main version = Version.Main.PRODUCTION;
		MongodConfig mongodConfig = MongodConfig.builder()
				.version(version)
				.stopTimeoutInMillis(5L)
				.net(new Net(Network.getFreeServerPort(), Network.localhostIsIPv6()))
				.cmdOptions(getCmdOptions(version))
				.build();

		RuntimeConfig runtimeConfig = Defaults.runtimeConfigFor(Command.MongoD).build();

		for (int i = 0; i < loops; i++) {
			logger.info("Loop: {}", i);
			MongodExecutable mongodExe = MongodStarter.getInstance(runtimeConfig).prepare(mongodConfig);
			try {
				MongodProcess mongod = mongodExe.start();

				try (MongoClient mongo = new MongoClient(
                        new ServerAddress(mongodConfig.net().getServerAddress(), mongodConfig.net().getPort()))) {
                    DB db = mongo.getDB("test");
                    DBCollection col = db.createCollection("testCol", new BasicDBObject());
                    col.save(new BasicDBObject("testDoc", new Date()));
                }

				mongod.stop();
			} finally {
				mongodExe.stop();
			}
		}

	}

	@Test
	public void testStartMongodOnNonFreePort() throws IOException, InterruptedException {
		int port = Network.getFreeServerPort();

		final Version.Main version = Version.Main.PRODUCTION;
		MongodConfig mongodConfig = MongodConfig.builder()
				.version(version)
				.net(new Net(port, Network.localhostIsIPv6()))
				.cmdOptions(getCmdOptions(version))
				.build();

		RuntimeConfig runtimeConfig = Defaults.runtimeConfigFor(Command.MongoD).build();

		MongodExecutable mongodExe = MongodStarter.getInstance(runtimeConfig).prepare(mongodConfig);
		MongodProcess mongod = mongodExe.start();

		boolean innerMongodCouldNotStart = false;
		{
			Thread.sleep(500);

			MongodExecutable innerExe = MongodStarter.getInstance(runtimeConfig).prepare(mongodConfig);
			try {
				MongodProcess innerMongod = innerExe.start();
				assertNotNull(innerMongod);
			} catch (RuntimeException iox) {
				innerMongodCouldNotStart = true;
			} finally {
				innerExe.stop();
				Assert.assertTrue("inner Mongod could not start", innerMongodCouldNotStart);
			}
		}

		mongod.stop();
		mongodExe.stop();
	}

}
