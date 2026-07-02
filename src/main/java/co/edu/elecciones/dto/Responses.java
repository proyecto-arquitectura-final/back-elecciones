package co.edu.elecciones.dto; import java.util.*;
public class Responses {
 public record PredictionItem(String candidate,String party,double currentPercentage,double projectedPercentage,double probability,double uncertaintyMargin){}
 public record LiveSummary(long votes,double percentageTables,double participation,List<PredictionItem> leaders){}
 public record DashboardAdmin(long activeElections,long users,long parties,long candidates,long auditEvents){}
 public record ChatResponse(String answer,List<String> toolsUsed){}
 public record Tool(String name,String description,List<String> allowedRoles){}
}
