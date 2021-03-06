import java.io.IOException;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.MechJeb;
import krpc.client.services.MechJeb.ManeuverPlanner;
import krpc.client.services.MechJeb.NodeExecutor;
import krpc.client.services.MechJeb.OperationCourseCorrection;
import krpc.client.services.MechJeb.OperationKillRelVel;
import krpc.client.services.MechJeb.OperationTransfer;
import krpc.client.services.MechJeb.TimeReference;

public class RendezvousWithTarget {
	public static void main(String[] args) throws IOException, RPCException, StreamException {
		//This script assumes the vessel is in orbit, planes match and the target is set.
		Connection conn = Connection.newInstance("Rendezvous with a target");
		MechJeb mj = MechJeb.newInstance(conn);

		System.out.println("Planning Hohmann transfer");
		ManeuverPlanner planner = mj.getManeuverPlanner();
		OperationTransfer hohmann = planner.getOperationTransfer();
		hohmann.makeNode();

		//check for warnings
		String warning = hohmann.getErrorMessage();
		if(!warning.isEmpty())
			System.out.println(warning);

		//execute the node
		NodeExecutor nodeExecutor = mj.getNodeExecutor();
		RendezvousWithTarget.executeNode(conn, nodeExecutor);

		//fine tune closest approach to the target
		System.out.println("Correcting course");
		OperationCourseCorrection fineTuneClosestApproach = planner.getOperationCourseCorrection();
		fineTuneClosestApproach.setInterceptDistance(50); //50 meters seems to be optimal distance; if you use 0, you can hit the target
		fineTuneClosestApproach.makeNode();
		nodeExecutor.setTolerance(0.01); //do a high-precision maneuver (0.01 dV tolerance)
		RendezvousWithTarget.executeNode(conn, nodeExecutor);

		System.out.println("Matching speed with the target");
		OperationKillRelVel matchSpeed = planner.getOperationKillRelVel();
		matchSpeed.getTimeSelector().setTimeReference(TimeReference.CLOSEST_APPROACH); //match speed at the closest approach
		matchSpeed.makeNode();
		nodeExecutor.setTolerance(0.1); //return the precision back to normal
		RendezvousWithTarget.executeNode(conn, nodeExecutor);

		System.out.println("Rendezvous complete!");
		conn.close();
	}

	private static void executeNode(Connection conn, NodeExecutor ne) throws StreamException, RPCException {
		System.out.println("Executing next maneuver node");
		ne.executeOneNode();

		Stream<Boolean> enabled = conn.addStream(ne, "getEnabled");
		enabled.setRate(1); //we don't need a high throughput rate, 1 second is more than enough
		synchronized(enabled.getCondition()) {
			while(enabled.get())
				enabled.waitForUpdate();
		}
		enabled.remove();
	}
}
