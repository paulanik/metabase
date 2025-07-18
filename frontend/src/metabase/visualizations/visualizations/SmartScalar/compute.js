import dayjs from "dayjs";
import { t } from "ttag";
import _ from "underscore";

import { formatValue } from "metabase/lib/formatting";
import { formatDateTimeRangeWithUnit } from "metabase/lib/formatting/date";
import { isEmpty } from "metabase/lib/validate";
import { computeChange } from "metabase/visualizations/lib/numeric";
import { COMPARISON_TYPES } from "metabase/visualizations/visualizations/SmartScalar/constants";
import { formatChange } from "metabase/visualizations/visualizations/SmartScalar/utils";
import * as Lib from "metabase-lib";
import { isDate } from "metabase-lib/v1/types/utils/isa";
import { isAbsoluteDateTimeUnit } from "metabase-types/guards/date-time";

export function computeTrend(series, insights, settings, { getColor }) {
  try {
    const comparisons = settings["scalar.comparisons"] || [];
    const currentMetricData = getCurrentMetricData({
      series,
      insights,
      settings,
    });

    const { clicked, date, dateUnitSettings, formatOptions, value } =
      currentMetricData;

    const displayValue = formatValue(value, formatOptions);
    const displayDate = formatDateStr({ date, dateUnitSettings });

    return {
      trend: {
        value,
        clicked,
        formatOptions,
        display: {
          value: displayValue,
          date: displayDate,
        },
        comparisons: comparisons.map((comparison) =>
          buildComparisonObject({
            comparison,
            currentMetricData,
            series,
            settings,
            getColor,
          }),
        ),
      },
    };
  } catch (error) {
    return {
      error,
    };
  }
}

function buildComparisonObject({
  comparison,
  currentMetricData,
  series,
  settings,
  getColor,
}) {
  const { formatOptions, value } = currentMetricData;

  const { comparisonDescStr, comparisonValue } =
    computeComparison({
      comparison,
      currentMetricData,
      series,
    }) || {};

  const percentChange = !isEmpty(comparisonValue)
    ? computeChange(comparisonValue, value)
    : undefined;

  const {
    changeType,
    changeArrowIconName,
    percentChangeStr,
    comparisonValueStr,
  } = computeChangeTypeWithOptions({
    comparisonValue,
    formatOptions,
    percentChange,
  });

  const changeColor = !isEmpty(changeArrowIconName)
    ? getArrowColor(
        changeArrowIconName,
        settings["scalar.switch_positive_negative"],
        { getColor },
      )
    : undefined;

  return {
    changeArrowIconName,
    changeColor,
    changeType,
    comparisonDescStr,
    comparisonValue,
    display: {
      percentChange: percentChangeStr,
      comparisonValue: comparisonValueStr,
    },
    percentChange,
  };
}

function computeComparison({ comparison, currentMetricData, series }) {
  const { type } = comparison;

  if (type === COMPARISON_TYPES.ANOTHER_COLUMN) {
    return computeTrendAnotherColumn({
      comparison,
      currentMetricData,
      series,
    });
  }

  if (type === COMPARISON_TYPES.PREVIOUS_VALUE) {
    return computeTrendPreviousValue({
      currentMetricData,
      series,
    });
  }

  if (
    type === COMPARISON_TYPES.PREVIOUS_PERIOD ||
    type === COMPARISON_TYPES.PERIODS_AGO
  ) {
    return computeTrendPeriodsAgo({
      comparison,
      currentMetricData,
      series,
    });
  }

  if (type === COMPARISON_TYPES.STATIC_NUMBER) {
    return computeTrendStaticValue({ comparison });
  }

  throw Error("Invalid comparison type specified.");
}

function getCurrentMetricData({ series, insights, settings }) {
  const [
    {
      card: {
        dataset_query: { type: queryType },
      },
      data: { rows, cols },
    },
  ] = series;

  // column locations for date and metric
  const dimensionColIndex = cols.findIndex((col) => {
    return isDate(col) || isAbsoluteDateTimeUnit(col.unit);
  });
  const metricColIndex = cols.findIndex(
    (col) => col.name === settings["scalar.field"],
  );

  if (dimensionColIndex === -1) {
    throw Error("No date column was found.");
  }

  if (metricColIndex === -1) {
    throw Error(
      "There was a problem with the primary number you chose. Check the viz settings and select a valid column for the primary number field.",
    );
  }

  // get latest value and date
  const latestRowIndex = _.findLastIndex(rows, (row) => {
    const date = row[dimensionColIndex];
    const value = row[metricColIndex];

    return !isEmpty(value) && !isEmpty(date);
  });
  if (latestRowIndex === -1) {
    throw Error("No rows contain a valid value.");
  }
  const date = rows[latestRowIndex][dimensionColIndex];
  const value = rows[latestRowIndex][metricColIndex];

  // get metric column metadata
  const metricColumn = cols[metricColIndex];
  const metricInsight = insights?.find(
    (insight) => insight.col === metricColumn.name,
  );
  const dateUnit = metricInsight?.unit;
  const dateColumn = cols[dimensionColIndex];
  const dateColummnWithUnit = { ...dateColumn };
  dateColummnWithUnit.unit ??= dateUnit;
  const dateColumnSettings = settings?.column?.(dateColummnWithUnit) ?? {};

  const dateUnitSettings = {
    dateColumn: dateColummnWithUnit,
    dateColumnSettings,
    dateUnit,
    queryType,
  };

  const formatOptions = {
    ...settings.column?.(metricColumn),
    compact: settings["scalar.compact_primary_number"],
  };

  const clicked = {
    value,
    column: cols[metricColIndex],
    dimensions: [
      {
        value: rows[latestRowIndex][dimensionColIndex],
        column: cols[dimensionColIndex],
      },
    ],
    data: rows[latestRowIndex].map((value, index) => ({
      value,
      col: cols[index],
    })),
    settings,
  };

  return {
    clicked,
    date,
    dateUnitSettings,
    formatOptions,
    indexData: {
      dimensionColIndex,
      metricColIndex,
      latestRowIndex,
    },
    value,
  };
}

function computeTrendAnotherColumn({ comparison, currentMetricData, series }) {
  const { latestRowIndex } = currentMetricData.indexData;
  const { cols, rows } = series[0].data;

  const columnIndex = cols.findIndex(
    (column) => column.name === comparison.column,
  );

  if (columnIndex === -1) {
    return {
      comparisonValueStr: t`(No data)`,
      comparisonDescStr: t`vs. N/A`,
    };
  }

  const column = cols[columnIndex];

  const lastRow = rows[latestRowIndex];
  const comparisonValue = lastRow[columnIndex];

  const displayName = comparison.label || column.display_name;

  return {
    comparisonDescStr: t`vs. ${displayName}`,
    comparisonValue,
  };
}

function computeTrendStaticValue({ comparison }) {
  const { value, label } = comparison;
  return {
    comparisonDescStr: t`vs. ${label}`,
    comparisonValue: value,
  };
}

function computeTrendPreviousValue({ currentMetricData, series }) {
  const [
    {
      data: { rows },
    },
  ] = series;

  const {
    date,
    dateUnitSettings,
    indexData: { dimensionColIndex, metricColIndex, latestRowIndex },
  } = currentMetricData;

  return computeComparisonPreviousValue({
    rows,
    dimensionColIndex,
    metricColIndex,
    nextValueRowIndex: latestRowIndex,
    nextDate: date,
    dateUnitSettings,
  });
}

function computeComparisonPreviousValue({
  rows,
  dimensionColIndex,
  metricColIndex,
  nextValueRowIndex,
  nextDate,
  dateUnitSettings,
}) {
  const previousRowIndex = _.findLastIndex(rows, (row, i) => {
    if (i >= nextValueRowIndex) {
      return false;
    }

    const date = row[dimensionColIndex];
    const value = row[metricColIndex];

    return !isEmpty(value) && !isEmpty(date);
  });

  // if no row exists with non-null date and non-null value
  if (previousRowIndex === -1) {
    return null;
  }

  const prevDate = rows[previousRowIndex][dimensionColIndex];
  const prevValue = rows[previousRowIndex][metricColIndex];

  const comparisonDescStr = computeComparisonStrPreviousValue({
    nextDate,
    prevDate,
    dateUnitSettings,
  });

  return {
    comparisonDescStr,
    comparisonValue: prevValue,
  };
}

function computeTrendPeriodsAgo({ comparison, currentMetricData, series }) {
  const [
    {
      data: { rows },
    },
  ] = series;

  const {
    date,
    dateUnitSettings,
    indexData: { dimensionColIndex, metricColIndex, latestRowIndex },
  } = currentMetricData;

  if (isEmpty(dateUnitSettings.dateUnit)) {
    throw Error("No date unit supplied for periods ago comparison.");
  }

  const { type, value } = comparison;
  if (type === COMPARISON_TYPES.PERIODS_AGO && !Number.isInteger(value)) {
    throw Error("No integer value supplied for periods ago comparison.");
  }
  const dateUnitsAgo = value ?? 1;

  return computeComparisonPeriodsAgo({
    rows,
    dimensionColIndex,
    metricColIndex,
    nextValueRowIndex: latestRowIndex,
    nextDate: date,
    dateUnitSettings,
    dateUnitsAgo,
  });
}

function computeComparisonPeriodsAgo({
  rows,
  dimensionColIndex,
  metricColIndex,
  nextValueRowIndex,
  nextDate,
  dateUnitSettings,
  dateUnitsAgo,
}) {
  const dateUnitDisplay = Lib.describeTemporalUnit(
    dateUnitSettings.dateUnit,
  ).toLowerCase();

  const computedPrevDate = dayjs
    .parseZone(nextDate)
    .subtract(dateUnitsAgo, dateUnitSettings.dateUnit)
    .format();

  const rowPeriodsAgo = getRowOfPeriodsAgo({
    prevDate: computedPrevDate,
    dateUnit: dateUnitSettings.dateUnit,
    dateUnitsAgo,
    dimensionColIndex,
    metricColIndex,
    nextValueRowIndex,
    rows,
  });

  const prevDate = !isEmpty(rowPeriodsAgo)
    ? rowPeriodsAgo[dimensionColIndex]
    : computedPrevDate;
  const comparisonDescStr =
    dateUnitsAgo === 1
      ? t`vs. previous ${dateUnitDisplay}`
      : computeComparisonStrPreviousValue({
          dateUnitSettings,
          nextDate,
          prevDate,
        });

  // if no row exists with date "X periods ago"
  if (isEmpty(rowPeriodsAgo)) {
    return {
      comparisonDescStr,
    };
  }

  const prevValue = rowPeriodsAgo[metricColIndex];

  return {
    comparisonDescStr,
    comparisonValue: prevValue,
  };
}

function getRowOfPeriodsAgo({
  prevDate,
  dateUnit,
  dateUnitsAgo,
  dimensionColIndex,
  metricColIndex,
  nextValueRowIndex,
  rows,
}) {
  const targetDate = dayjs.parseZone(prevDate);
  // skip the latest element since that is our current value
  const searchIndexStart = nextValueRowIndex - 1;
  if (searchIndexStart < 0) {
    return undefined;
  }

  // only look dateUnitsAgo elements (dates) into the past,
  // since looking any further would automatically result in a date before
  // X periods ago and any prior dates would be further beyond our desired
  // comparison date
  const lastCandidateIndex = searchIndexStart - (dateUnitsAgo - 1);
  const searchIndexEnd = lastCandidateIndex >= 0 ? lastCandidateIndex : 0;

  for (let i = searchIndexStart; i >= searchIndexEnd; i--) {
    const candidateRow = rows[i];
    const candidateDate = dayjs.parseZone(candidateRow[dimensionColIndex]);
    const candidateValue = candidateRow[metricColIndex];

    if (
      areDatesTheSame({ candidateDate, dateUnit, targetDate }) &&
      !isEmpty(candidateValue)
    ) {
      return candidateRow;
    }

    // if current candidate is before the targetDate, we can stop searching
    // because previous rows will only be further in the past
    if (targetDate.diff(candidateDate, dateUnit) > 0) {
      return undefined;
    }
  }

  return undefined;
}

function areDatesTheSame({ candidateDate, targetDate, dateUnit }) {
  if (targetDate.diff(candidateDate, dateUnit) !== 0) {
    return false;
  }

  // if dates have different time-zones, the above check can be bypassed
  // i.e. if the candidateDate has a more negative offset than the targetDate
  // the comparison can result in a diff of 0 because the candidateDate
  // is not one full dateUnit behind, only partially (0 < x < 1) behind
  // examples: targetDate: 12-01-2023T00:00-04:00 (-4 offset)
  //           candidateDate: 11-01-2023T00:00-05:00 (-5 offset)
  //                       =: 11-01-2023T01:00-04:00
  //           targetDate.diff(candidateDate, "month") === 0 (true)
  // so in order to account for this, we should check to make sure the
  // dateUnit number is the same as well
  if (targetDate?.[dateUnit]() !== candidateDate?.[dateUnit]()) {
    return false;
  }

  return true;
}

function computeComparisonStrPreviousValue({
  dateUnitSettings,
  prevDate,
  nextDate,
}) {
  const isSameDay = dayjs.parseZone(prevDate).isSame(nextDate, "day");
  const isSameYear = dayjs.parseZone(prevDate).isSame(nextDate, "year");

  const options = {
    removeDay: isSameDay,
    removeYear: isSameYear,
  };

  const formattedDateStr = formatDateStr({
    date: prevDate,
    dateUnitSettings,
    options,
  });

  return t`vs. ${formattedDateStr}`;
}

function formatDateStr({ date, dateUnitSettings, options }) {
  const { dateColumn, dateColumnSettings, dateUnit, queryType } =
    dateUnitSettings;

  if (isEmpty(dateUnit) || queryType === "native") {
    return formatValue(date, {
      ...dateColumnSettings,
      column: dateColumn,
    });
  }

  return formatDateTimeRangeWithUnit([date], dateUnit, {
    ...options,
    compact: true,
  });
}

export const CHANGE_TYPE_OPTIONS = {
  get MISSING() {
    return {
      CHANGE_TYPE: "PREVIOUS_VALUE_MISSING",
      PERCENT_CHANGE_STR: t`N/A`,
      COMPARISON_VALUE_STR: t`(No data)`,
    };
  },
  get SAME() {
    return {
      CHANGE_TYPE: "PREVIOUS_VALUE_SAME",
      PERCENT_CHANGE_STR: t`No change`,
      COMPARISON_VALUE_STR: "",
    };
  },
  get CHANGED() {
    return {
      CHANGE_TYPE: "PREVIOUS_VALUE_CHANGED",
    };
  },
};

export const CHANGE_ARROW_ICONS = {
  ARROW_UP: "arrow_up",
  ARROW_DOWN: "arrow_down",
};

function computeChangeTypeWithOptions({
  comparisonValue,
  formatOptions,
  percentChange,
}) {
  if (isEmpty(comparisonValue)) {
    return {
      changeType: CHANGE_TYPE_OPTIONS.MISSING.CHANGE_TYPE,
      percentChangeStr: CHANGE_TYPE_OPTIONS.MISSING.PERCENT_CHANGE_STR,
      comparisonValueStr: CHANGE_TYPE_OPTIONS.MISSING.COMPARISON_VALUE_STR,
    };
  }

  if (percentChange === 0) {
    return {
      changeType: CHANGE_TYPE_OPTIONS.SAME.CHANGE_TYPE,
      percentChangeStr: CHANGE_TYPE_OPTIONS.SAME.PERCENT_CHANGE_STR,
      comparisonValueStr: CHANGE_TYPE_OPTIONS.SAME.COMPARISON_VALUE_STR,
    };
  }

  return {
    changeType: CHANGE_TYPE_OPTIONS.CHANGED.CHANGE_TYPE,
    changeArrowIconName:
      percentChange < 0
        ? CHANGE_ARROW_ICONS.ARROW_DOWN
        : CHANGE_ARROW_ICONS.ARROW_UP,
    percentChangeStr: formatChange(percentChange),
    comparisonValueStr: formatValue(comparisonValue, formatOptions),
  };
}

function getArrowColor(
  changeArrowIconName,
  shouldSwitchPositiveNegative,
  { getColor },
) {
  const arrowIconColorNames = shouldSwitchPositiveNegative
    ? {
        [CHANGE_ARROW_ICONS.ARROW_DOWN]: getColor("success"),
        [CHANGE_ARROW_ICONS.ARROW_UP]: getColor("error"),
      }
    : {
        [CHANGE_ARROW_ICONS.ARROW_DOWN]: getColor("error"),
        [CHANGE_ARROW_ICONS.ARROW_UP]: getColor("success"),
      };

  return arrowIconColorNames[changeArrowIconName];
}
