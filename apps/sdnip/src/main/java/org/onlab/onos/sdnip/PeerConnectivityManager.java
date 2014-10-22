package org.onlab.onos.sdnip;

import org.onlab.onos.ApplicationId;
import org.onlab.onos.net.ConnectPoint;
import org.onlab.onos.net.flow.DefaultTrafficSelector;
import org.onlab.onos.net.flow.DefaultTrafficTreatment;
import org.onlab.onos.net.flow.TrafficSelector;
import org.onlab.onos.net.flow.TrafficTreatment;
import org.onlab.onos.net.intent.IntentService;
import org.onlab.onos.net.intent.PointToPointIntent;
import org.onlab.onos.sdnip.config.BgpPeer;
import org.onlab.onos.sdnip.config.BgpSpeaker;
import org.onlab.onos.sdnip.config.Interface;
import org.onlab.onos.sdnip.config.InterfaceAddress;
import org.onlab.onos.sdnip.config.SdnIpConfigService;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Manages the connectivity requirements between peers.
 */
public class PeerConnectivityManager {

    private static final Logger log = LoggerFactory.getLogger(
            PeerConnectivityManager.class);

    // TODO these shouldn't be defined here
    private static final short BGP_PORT = 179;
    private static final int IPV4_BIT_LENGTH = 32;

    private final SdnIpConfigService configInfoService;
    private final InterfaceService interfaceService;
    private final IntentService intentService;

    private final ApplicationId appId;

    public PeerConnectivityManager(ApplicationId appId,
                                   SdnIpConfigService configInfoService,
                                   InterfaceService interfaceService,
                                   IntentService intentService) {
        this.appId = appId;
        this.configInfoService = configInfoService;
        this.interfaceService = interfaceService;
        this.intentService = intentService;
    }

    public void start() {
        // TODO are any of these errors?
        if (interfaceService.getInterfaces().isEmpty()) {

            log.warn("The interface in configuration file is empty. "
                             + "Thus, the SDN-IP application can not be started.");
        } else if (configInfoService.getBgpPeers().isEmpty()) {

            log.warn("The BGP peer in configuration file is empty."
                             + "Thus, the SDN-IP application can not be started.");
        } else if (configInfoService.getBgpSpeakers() == null) {

            log.error("The BGP speaker in configuration file is empty. "
                              + "Thus, the SDN-IP application can not be started.");
            return;
        }

        setupBgpPaths();
        setupIcmpPaths();
    }

    /**
     * Sets up paths for all {@link BgpSpeaker}s and all external peers.
     * <p/>
     * Run a loop for all BGP speakers and a loop for all BGP peers outside.
     * Push intents for paths from each BGP speaker to all peers. Push intents
     * for paths from all peers to each BGP speaker.
     */
    private void setupBgpPaths() {
        for (BgpSpeaker bgpSpeaker : configInfoService.getBgpSpeakers()
                .values()) {
            log.debug("Start to set up BGP paths for BGP speaker: {}",
                      bgpSpeaker);
            ConnectPoint bgpdConnectPoint = bgpSpeaker.connectPoint();

            List<InterfaceAddress> interfaceAddresses =
                    bgpSpeaker.interfaceAddresses();

            for (BgpPeer bgpPeer : configInfoService.getBgpPeers().values()) {

                log.debug("Start to set up BGP paths between BGP speaker: {} "
                                  + "to BGP peer: {}", bgpSpeaker, bgpPeer);

                Interface peerInterface = interfaceService.getInterface(
                        bgpPeer.connectPoint());
                if (peerInterface == null) {
                    log.error("Can not find the corresponding Interface from "
                                      + "configuration for BGP peer {}",
                              bgpPeer.ipAddress());
                    continue;
                }

                IpAddress bgpdAddress = null;
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    if (interfaceAddress.connectPoint().equals(
                            peerInterface.connectPoint())) {
                        bgpdAddress = interfaceAddress.ipAddress();
                        break;
                    }
                }
                if (bgpdAddress == null) {
                    log.debug("There is no interface IP address for bgpPeer: {}"
                                      + " on interface {}", bgpPeer, bgpPeer.connectPoint());
                    return;
                }

                IpAddress bgpdPeerAddress = bgpPeer.ipAddress();
                ConnectPoint bgpdPeerConnectPoint = peerInterface.connectPoint();

                // install intent for BGP path from BGPd to BGP peer matching
                // destination TCP port 179

                // TODO: The usage of PacketMatchBuilder will be improved, then we
                // only need to new the PacketMatchBuilder once.
                // By then, the code here will be improved accordingly.
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_TCP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchTcpDst(BGP_PORT)
                        .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .build();

                PointToPointIntent intentMatchDstTcpPort =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdConnectPoint, bgpdPeerConnectPoint);
                intentService.submit(intentMatchDstTcpPort);
                log.debug("Submitted BGP path intent matching dst TCP port 179 "
                                  + "from BGPd {} to peer {}: {}",
                          bgpdAddress, bgpdPeerAddress, intentMatchDstTcpPort);

                // install intent for BGP path from BGPd to BGP peer matching
                // source TCP port 179
                selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_TCP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchTcpSrc(BGP_PORT)
                        .build();

                PointToPointIntent intentMatchSrcTcpPort =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdConnectPoint, bgpdPeerConnectPoint);
                intentService.submit(intentMatchSrcTcpPort);
                log.debug("Submitted BGP path intent matching src TCP port 179"
                                  + "from BGPd {} to peer {}: {}",
                          bgpdAddress, bgpdPeerAddress, intentMatchSrcTcpPort);

                // install intent for reversed BGP path from BGP peer to BGPd
                // matching destination TCP port 179
                selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_TCP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchTcpDst(BGP_PORT)
                        .build();

                PointToPointIntent reversedIntentMatchDstTcpPort =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdPeerConnectPoint, bgpdConnectPoint);
                intentService.submit(reversedIntentMatchDstTcpPort);
                log.debug("Submitted BGP path intent matching dst TCP port 179"
                                  + "from BGP peer {} to BGPd {} : {}",
                          bgpdPeerAddress, bgpdAddress, reversedIntentMatchDstTcpPort);

                // install intent for reversed BGP path from BGP peer to BGPd
                // matching source TCP port 179
                selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_TCP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchTcpSrc(BGP_PORT)
                        .build();

                PointToPointIntent reversedIntentMatchSrcTcpPort =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdPeerConnectPoint, bgpdConnectPoint);
                intentService.submit(reversedIntentMatchSrcTcpPort);
                log.debug("Submitted BGP path intent matching src TCP port 179"
                                  + "from BGP peer {} to BGPd {} : {}",
                          bgpdPeerAddress, bgpdAddress, reversedIntentMatchSrcTcpPort);

            }
        }
    }

    /**
     * Sets up ICMP paths between each {@link BgpSpeaker} and all BGP peers
     * located in other external networks.
     * <p/>
     * Run a loop for all BGP speakers and a loop for all BGP Peers. Push
     * intents for paths from each BGP speaker to all peers. Push intents
     * for paths from all peers to each BGP speaker.
     */
    private void setupIcmpPaths() {
        for (BgpSpeaker bgpSpeaker : configInfoService.getBgpSpeakers()
                .values()) {
            log.debug("Start to set up ICMP paths for BGP speaker: {}",
                      bgpSpeaker);
            ConnectPoint bgpdConnectPoint = bgpSpeaker.connectPoint();
            List<InterfaceAddress> interfaceAddresses = bgpSpeaker
                    .interfaceAddresses();

            for (BgpPeer bgpPeer : configInfoService.getBgpPeers().values()) {

                Interface peerInterface = interfaceService.getInterface(
                        bgpPeer.connectPoint());

                if (peerInterface == null) {
                    log.error("Can not find the corresponding Interface from "
                                      + "configuration for BGP peer {}",
                              bgpPeer.ipAddress());
                    continue;
                }
                IpAddress bgpdAddress = null;
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    if (interfaceAddress.connectPoint().equals(
                            peerInterface.connectPoint())) {
                        bgpdAddress = interfaceAddress.ipAddress();
                        break;
                    }

                }
                if (bgpdAddress == null) {
                    log.debug("There is no IP address for bgpPeer: {} on "
                                      + "interface port: {}", bgpPeer,
                              bgpPeer.connectPoint());
                    return;
                }

                IpAddress bgpdPeerAddress = bgpPeer.ipAddress();
                ConnectPoint bgpdPeerConnectPoint = peerInterface.connectPoint();

                // install intent for ICMP path from BGPd to BGP peer
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .build();

                PointToPointIntent intent =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdConnectPoint, bgpdPeerConnectPoint);
                intentService.submit(intent);
                log.debug("Submitted ICMP path intent from BGPd {} to peer {} :"
                                  + " {}", bgpdAddress, bgpdPeerAddress, intent);

                // install intent for reversed ICMP path from BGP peer to BGPd
                selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                        .matchIPSrc(IpPrefix.valueOf(bgpdPeerAddress.toInt(), IPV4_BIT_LENGTH))
                        .matchIPDst(IpPrefix.valueOf(bgpdAddress.toInt(), IPV4_BIT_LENGTH))
                        .build();

                PointToPointIntent reversedIntent =
                        new PointToPointIntent(appId, selector, treatment,
                                               bgpdPeerConnectPoint, bgpdConnectPoint);
                intentService.submit(reversedIntent);
                log.debug("Submitted ICMP path intent from BGP peer {} to BGPd"
                                  + " {} : {}",
                          bgpdPeerAddress, bgpdAddress, reversedIntent);
            }
        }
    }

}