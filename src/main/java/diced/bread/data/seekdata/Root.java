package diced.bread.data.seekdata;

import java.util.ArrayList;

public class Root {
    public ArrayList<Datum> data;
    public int totalCount;
    public Info info;
    public String userQueryId;
    public Location location;
    public ArrayList<SortMode> sortModes;
    public SolMetadata solMetadata;
    public Facets facets;
    public SearchParams searchParams;
}