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
package de.flapdoodle.embed.mongo.runtime;

import java.util.List;
import java.util.Objects;

import de.flapdoodle.embed.mongo.config.MongoCommonConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.packageresolver.Feature;


public abstract class AbstractMongo {

    protected static <T extends MongoCommonConfig> void applyDefaultOptions(T config, List<String> ret) {
        if (!config.version().enabled(Feature.NO_HTTP_INTERFACE_ARG)) {
            ret.add("--nohttpinterface");
        }
    }

    protected static <T extends MongoCommonConfig> void applyNet(T config, List<String> ret) {
        Net net = config.net();

        ret.add("--port");
        ret.add("" + net.getPort());
        if (net.isIpv6()) {
            ret.add("--ipv6");
        }
        if (net.getBindIp() != null) {
            ret.add("--bind_ip");
            if (Objects.equals("localhost", net.getBindIp())
                    && config.version().enabled(Feature.NO_BIND_IP_TO_LOCALHOST)) {
                ret.add("127.0.0.1");
            } else {
                ret.add(net.getBindIp());
            }
        }
    }

}
