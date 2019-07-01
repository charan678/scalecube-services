package io.scalecube.services.transport.api;

import io.scalecube.net.Address;
import io.scalecube.services.transport.api.ClientChannel;

/** Client service transport factory interface. */
@FunctionalInterface
public interface ClientTransportFactory {


  /**
   * Creates a client channel ready for communication with remote service node.
   *
   * @param address address to connect
   * @return client channel instance.
   */
  ClientChannel create(Address address);
}