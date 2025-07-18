import { t } from "ttag";

import { Flex, HoverCard, Icon, Stack, Switch, Text } from "metabase/ui";

import S from "./RequiredParamToggle.module.css";

interface RequiredParamToggleProps {
  disabled?: boolean;
  uniqueId: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabledTooltip: React.ReactNode;
}

export function RequiredParamToggle(props: RequiredParamToggleProps) {
  const { disabled, value, onChange, uniqueId, disabledTooltip } = props;
  const id = `required_param_toggle_${uniqueId}`;

  return (
    <Flex gap="sm" mt="md">
      <Switch
        disabled={disabled}
        id={id}
        checked={value}
        onChange={(event) => onChange(event.currentTarget.checked)}
      />
      <div>
        <label className={S.SettingRequiredLabel} htmlFor={id}>
          {t`Always require a value`}
          {disabled && (
            <HoverCard position="top-end" shadow="xs">
              <HoverCard.Target>
                <Icon name="info_filled" />
              </HoverCard.Target>
              <HoverCard.Dropdown w={320}>
                <Stack p="md" gap="sm">
                  {disabledTooltip}
                </Stack>
              </HoverCard.Dropdown>
            </HoverCard>
          )}
        </label>

        <Text
          mt="sm"
          lh={1.2}
        >{t`When enabled, people can change the value or reset it, but can't clear it entirely.`}</Text>
      </div>
    </Flex>
  );
}
