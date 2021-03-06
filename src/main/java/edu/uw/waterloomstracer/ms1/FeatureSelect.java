package edu.uw.waterlooms.ms1;

import edu.uw.waterlooms.entity.SVRScore;
import java.io.*;
import java.util.*;

/**
 * MSTracer FeatureSelect Class
 *
 * @author Xiangyuan Zeng
 */
public class FeatureSelect {
  private int id_index;
  private int mz_index;
  private int rt_index;
  private int z_index;
  private int isonum_index;
  private int int_shape_index;
  private int iso_distr_index;
  private int intensity_window_avg_index;
  private int intensity_area_percentage_index;
  private int rt_start_index;
  private int rt_end_index;
  private int scan_num_index;
  private int intensity_sum_index;
  private int svr_index;
  private int quality_index;
  private int invalidVal;
  private double mz_error;
  private double rt_error;
  private List<Double[]> model_result; // read _svr_nn_score

  enum ML {
    SVR,
    NN
  }
  /**
   * Select charge state based on SVR score; one charge state of a feature
   *
   * @param filepath file path without defined suffix
   * @param prop parameters in the form of Properties read from a file
   * @throws IOException
   */
  public void selectFeature(String filepath, Properties prop) throws IOException {
    setParameters(prop);
    ML ml = ML.SVR;
    model_result = readFile(filepath + "_svr_score");
    selectCharge();
    model_result = removeIso(model_result);
    writeFile(filepath + "_feature_one_z", model_result, ml);
  }
  /**
   * Generate the final feature list; last step
   *
   * @param filepath file path without defined suffix
   * @param prop parameters in the form of Properties read from a file
   * @throws IOException
   */
  public List<SVRScore> finalizeFeature(String filepath, Properties prop) throws IOException {
    setParameters(prop);
    ML ml = ML.NN;
    model_result = readFile(filepath + "_nn_score");
    model_result = clusterFeature(model_result, mz_error, rt_error);
    model_result.sort(Comparator.comparing(l -> l[quality_index]));
    Collections.reverse(model_result);

    List<SVRScore> svrScores = new ArrayList<>();
    for (Double[] element : model_result) {
      SVRScore ms1Precursor =
          new SVRScore(
              element[0],
              element[1],
              element[2],
              element[3],
              element[4],
              element[5],
              element[6],
              element[7],
              element[8],
              element[9],
              element[10],
              element[11],
              element[12],
              element[13],
              element[14]);
      svrScores.add(ms1Precursor);
    }

    writeFile(filepath + "_feature", model_result, ml);

    return svrScores;
  }

  /**
   * Helper function for constructor. Sets parameter values defined above.
   *
   * @param properties
   */
  private void setParameters(Properties properties) {
    id_index = Integer.parseInt(properties.getProperty("ID_INDEX"));
    mz_index = Integer.parseInt(properties.getProperty("MZ_INDEX"));
    rt_index = Integer.parseInt(properties.getProperty("RT_INDEX"));
    z_index = Integer.parseInt(properties.getProperty("Z_INDEX"));
    isonum_index = Integer.parseInt(properties.getProperty("ISONUM_INDEX"));
    int_shape_index = Integer.parseInt(properties.getProperty("INT_SHAPE_INDEX"));
    iso_distr_index = Integer.parseInt(properties.getProperty("ISO_DISTR_INDEX"));
    intensity_window_avg_index =
        Integer.parseInt(properties.getProperty("INTENSITY_WINDOW_AVG_INDEX"));
    intensity_area_percentage_index =
        Integer.parseInt(properties.getProperty("INTENSITY_AREA_PERCENTAGE_INDEX"));
    rt_start_index = Integer.parseInt(properties.getProperty("RT_START_INDEX"));
    rt_end_index = Integer.parseInt(properties.getProperty("RT_END_INDEX"));
    scan_num_index = Integer.parseInt(properties.getProperty("SCAN_NUM_INDEX"));
    intensity_sum_index = Integer.parseInt(properties.getProperty("INTENSITY_SUM_INDEX"));
    svr_index = Integer.parseInt(properties.getProperty("SVR_INDEX"));
    quality_index = Integer.parseInt(properties.getProperty("QUALITY_INDEX"));
    invalidVal = Integer.parseInt(properties.getProperty("INVALID_VAL"));
    mz_error = Double.parseDouble(properties.getProperty("MZ_ERROR"));
    rt_error = Double.parseDouble(properties.getProperty("RT_ERROR"));
  }

  public void selectCharge() {
    model_result.sort(Comparator.comparing(l -> l[rt_index]));
    model_result.sort(Comparator.comparing(l -> l[mz_index]));
    for (int i = 0; i < model_result.size(); ) {
      double cur_mz = model_result.get(i)[mz_index];
      double cur_rt = model_result.get(i)[rt_index];
      if (i == model_result.size() - 1) break;
      int best_index = i;

      double best_score = model_result.get(i)[svr_index];
      for (int j = 1; i + j < model_result.size(); j++) {
        double next_mz = model_result.get(i + j)[mz_index];
        double next_rt = model_result.get(i + j)[rt_index];
        double next_svr = model_result.get(i + j)[svr_index];
        if (cur_mz == next_mz && cur_rt == next_rt) {
          if (next_svr > best_score) {
            model_result.get(best_index)[svr_index] = (double) invalidVal;
            best_score = next_svr;
            best_index = i + j;
          } else {
            model_result.get(i + j)[svr_index] = (double) invalidVal;
          }
        } else {
          i += j;
          break;
        }
        if (i + j == model_result.size() - 1) {
          i = model_result.size();
        }
      }
    }
    model_result.sort(Comparator.comparing(l -> l[id_index]));
    int total = 0;
    for (int i = 0; i < model_result.size(); ) {
      int curPepNum = model_result.get(i)[isonum_index].intValue();
      int fst = 0;
      int last = curPepNum - 1;
      boolean is_valid = true;
      while (fst < curPepNum && model_result.get(i + fst)[svr_index] == invalidVal) {
        fst++;
      }
      while (last >= 0 && model_result.get(i + last)[svr_index] == invalidVal) {
        last--;
      }
      if (fst >= last) { // if there is only 1 left in the group or all are not left
        is_valid = false;
      } else {
        for (int j = fst; j <= last; j++) {
          if (model_result.get(i + j)[svr_index] == invalidVal) {
            is_valid = false;
            break;
          }
        }
      }
      if (!is_valid) {
        for (int j = 0; j < curPepNum; j++) {
          model_result.get(i + j)[svr_index] = (double) invalidVal;
        }
      } else total++;
      i += curPepNum;
    }
    // Delete invalid score
    List<Double[]> valid_model_result = new ArrayList<>();
    for (int i = 0; i < model_result.size(); i++) {
      if (model_result.get(i)[svr_index] != (double) invalidVal) {
        valid_model_result.add(model_result.get(i));
      }
    }
    model_result = valid_model_result;
    // Change isotope number in a group
    for (int i = 0; i < model_result.size(); ) {
      int cur_pepnum = 1;
      int cur_id = model_result.get(i)[id_index].intValue();
      int j = i + 1;
      for (; j < model_result.size(); j++) {
        int next_id = model_result.get(j)[id_index].intValue();
        if (cur_id == next_id) {
          cur_pepnum++;
        } else {
          break;
        }
      }
      for (int k = 0; k < cur_pepnum; k++) {
        model_result.get(i + k)[isonum_index] = (double) cur_pepnum;
      }
      i = j;
    }
    System.out.println("selectCharge completed");
  }
  /**
   * Cluster similar features
   *
   * @param isoList input isotopic feature lists
   * @param mz_error mz error for clustering
   * @param rt_error rt errot for clustering
   * @return clustered feature list
   */
  public List<Double[]> clusterFeature(List<Double[]> isoList, double mz_error, double rt_error) {
    List<Double[]> featureGroup = new ArrayList<>();
    List<Double[]> isoAllFeature = isoList;
    isoAllFeature.sort(Comparator.comparing(l -> l[rt_index]));
    isoAllFeature.sort(Comparator.comparing(l -> l[z_index]));
    isoAllFeature.sort(Comparator.comparing(l -> l[mz_index]));
    for (int i = 0; i < isoAllFeature.size(); i++) {
      double mz_iso1 = isoAllFeature.get(i)[mz_index];
      double rt_iso1 = isoAllFeature.get(i)[rt_index];
      double z_iso1 = isoAllFeature.get(i)[z_index];
      double cur_hi_score = isoAllFeature.get(i)[quality_index];
      int j = i + 1;
      Double[] newIso = isoAllFeature.get(i).clone();
      for (; j < isoAllFeature.size(); ) {
        double mz_iso2 = isoAllFeature.get(j)[mz_index];
        double rt_iso2 = isoAllFeature.get(j)[rt_index];
        double z_iso2 = isoAllFeature.get(j)[z_index];
        double score_iso2 = isoAllFeature.get(j)[quality_index];
        if (mz_iso2 <= mz_iso1 * (1 + mz_error)
            && mz_iso2 >= mz_iso1 * (1 - mz_error)
            && rt_iso2 >= rt_iso1 - rt_error
            && rt_iso2 <= rt_iso1 + rt_error
            && z_iso1 == z_iso2) {
          if (score_iso2 > cur_hi_score) {
            cur_hi_score = score_iso2;
            newIso = isoAllFeature.get(j).clone();
          }
          isoAllFeature.remove(j);
        } else if (mz_iso2 >= mz_iso1 * (1 + mz_error)) {
          break;
        } else if (mz_iso2 <= mz_iso1 * (1 - mz_error)) {
          break;
        } else {
          j++;
        }
      }
      featureGroup.add(newIso);
    }
    System.out.println("Peptide # after deleting duplicates: " + featureGroup.size());
    System.out.println("clusterFeature completed");
    return featureGroup;
  }

  /**
   * Removing redundant information of group
   *
   * @param isolist input isotopic feature lists
   * @return list leaving with one main isotope of the group
   */
  public List<Double[]> removeIso(List<Double[]> isolist) {
    List<Double[]> list = new ArrayList<>();
    for (int i = 0; i < isolist.size(); ) {
      list.add(isolist.get(i));
      i += isolist.get(i)[isonum_index];
    }
    return list;
  }

  static List<Double[]> readFile(String filename) throws IOException {
    List<Double[]> result = new ArrayList<>();
    FileReader fileReader = new FileReader(filename);
    BufferedReader bufferedReader = new BufferedReader(fileReader);
    String line;
    boolean IsfirstLine = true;
    while ((line = bufferedReader.readLine()) != null) {
      // process first line
      if (IsfirstLine == true) {
        IsfirstLine = false;
      } else {
        // process line
        String[] words = line.split("\\t");
        Double[] nums = new Double[words.length];
        for (int i = 0; i < words.length; i++) {
          nums[i] = Double.valueOf(words[i]);
        }
        result.add(nums);
      }
    }
    return result;
  }

  void writeFile(String filename, List<Double[]> list, ML ml) throws IOException {
    File file = new File(filename);
    try {
      // create FileWriter object with file as parameter
      FileWriter outputfile = new FileWriter(file);
      // PrintWriter
      PrintWriter printWriter = new PrintWriter(outputfile);
      // add header
      String nn_title = "";
      if (ml == ML.NN) {
        nn_title = '\t' + "quality_score";
      }
      String header =
          "id"
              + '\t'
              + "mz"
              + '\t'
              + "rt"
              + '\t'
              + "z"
              + '\t'
              + "isotope_num"
              + '\t'
              + "intensity_shape_score"
              + '\t'
              + "isotope_distribution_score"
              + '\t'
              + "intensity_window_evg"
              + '\t'
              + "intensity_area_percentage"
              + '\t'
              + "rt_start"
              + '\t'
              + "rt_end"
              + '\t'
              + "scan_num"
              + '\t'
              + "intensity_sum"
              + '\t'
              + "svr_score"
              + nn_title
              + '\n';
      printWriter.print(header);
      Integer id = 1;
      for (int i = 0; i < list.size(); ) {
        int size = 1;
        for (int j = 0; j < size; j++) {
          Integer z = list.get(i + j)[z_index].intValue();
          Integer iso_num = list.get(i + j)[isonum_index].intValue();
          String data = "";
          data += id.toString();
          data += '\t' + list.get(i + j)[mz_index].toString();
          data += '\t' + list.get(i + j)[rt_index].toString();
          data += '\t' + z.toString();
          data += '\t' + iso_num.toString();
          data += '\t' + list.get(i + j)[int_shape_index].toString();
          data += '\t' + list.get(i + j)[iso_distr_index].toString();
          data += '\t' + list.get(i + j)[intensity_window_avg_index].toString();
          data += '\t' + list.get(i + j)[intensity_area_percentage_index].toString();
          data += '\t' + list.get(i + j)[rt_start_index].toString();
          data += '\t' + list.get(i + j)[rt_end_index].toString();
          data += '\t' + list.get(i + j)[scan_num_index].toString();
          data += '\t' + list.get(i + j)[intensity_sum_index].toString();
          data += '\t' + list.get(i + j)[svr_index].toString();
          if (ml == ML.NN) {
            data += '\t' + list.get(i + j)[quality_index].toString();
            data += '\n';
          } else {
            data += '\n';
          }
          printWriter.print(data);
        }
        i += size;
        id++;
      }
      // closing writer connection
      printWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
